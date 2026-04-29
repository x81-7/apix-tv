import Foundation
import SwiftUI

@MainActor
final class AppViewModel: ObservableObject {
    enum LaunchState {
        case loading
        case gate
        case ready
        case blocked(BanVerdict)
    }

    @Published var launchState: LaunchState = .loading
    @Published var categories: [CategorySection] = []
    @Published var sideMenus: [String: [Channel]] = [:]
    @Published var sideMenuPins: [String: String] = [:]
    @Published var unlockedMenus: Set<String> = []
    @Published var selectedCategoryID: String?
    @Published var appSettings = AppSettings()
    @Published var gateConfig = GateConfig()
    @Published var searchQuery = ""
    @Published var errorMessage: String?

    /// Set when an external `apix://` or `https://apix-panal.vercel.app/watch.html`
    /// link is opened. RootView observes this and presents the player full-screen.
    @Published var pendingExternalStream: ExternalStream?

    func openExternalStream(_ stream: ExternalStream) {
        pendingExternalStream = stream
    }

    func clearExternalStream() {
        pendingExternalStream = nil
    }

    private let api = CloudAPI()
    private let handshake = HandshakeService()
    private let bypassKey = "ios_gate_bypass_code"

    func bootstrap() async {
        if case .loading = launchState {} else { return }

        // 1) Hydrate UI INSTANTLY from the encrypted on-device cache so the
        //    user sees content even before the network call returns. This
        //    matches the Android behaviour and slashes Supabase egress for
        //    repeat app opens.
        let cachedChannels = SecureCacheManager.loadChannels()
        let cachedSubs = SecureCacheManager.loadSubChannels()
        if !cachedChannels.isEmpty {
            let groupedCached = Dictionary(grouping: cachedChannels.filter { !($0.hidden ?? false) },
                                           by: { $0.categoryId ?? "" })
            // Flat synthetic categories from cache (ordered later by network refresh)
            categories = groupedCached
                .sorted { ($0.value.first?.sortOrder ?? 0) < ($1.value.first?.sortOrder ?? 0) }
                .map { (catID, items) in
                    CategorySection(id: catID, name: "", sortOrder: 0,
                                    channels: items.sorted { ($0.sortOrder ?? 0) < ($1.sortOrder ?? 0) })
                }
            sideMenus = cachedSubs
            selectedCategoryID = selectedCategoryID ?? categories.first?.id
            launchState = .ready
        }

        async let verdict = handshake.validateCurrentDevice()
        async let bundle = api.fetchBundle()
        async let settings = api.fetchAppSettings()
        async let gate = api.fetchGateConfig()

        let currentVerdict = await verdict
        if currentVerdict.status == "PERMA_BAN" || currentVerdict.status == "TEMP_BAN" || currentVerdict.status == "TAMPERED_MOD" {
            launchState = .blocked(currentVerdict)
            return
        }

        do {
            // Try the aggregated cached-data Edge Function first (single round
            // trip + ETag/304). Fall back to per-table REST when unavailable.
            let cats: [CategoryRow]
            let chans: [Channel]
            let menus: [SideMenu]
            let subs: [SubChannel]
            if let b = await bundle, !b.notModified {
                cats = b.categories
                chans = b.channels
                menus = b.sideMenus
                subs = b.subChannels
            } else if let b = await bundle, b.notModified, !cachedChannels.isEmpty {
                // 304 + we already hydrated UI from cache → just refresh
                // settings/gate and skip rebuilding the lists.
                appSettings = await settings
                gateConfig = await gate
                if gateConfig.enabled && storedBypassCode() != gateConfig.bypassCode {
                    launchState = .gate
                } else {
                    launchState = .ready
                }
                return
            } else {
                async let categoriesRows = api.fetchCategories()
                async let channelsRows = api.fetchChannels()
                async let sideMenusRows = api.fetchSideMenus()
                async let subChannelsRows = api.fetchSubChannels()
                cats = try await categoriesRows
                chans = try await channelsRows
                menus = try await sideMenusRows
                subs = try await subChannelsRows
            }
            appSettings = await settings
            gateConfig = await gate

            let visibleChannels = chans.filter { !($0.hidden ?? false) }
            let grouped = Dictionary(grouping: visibleChannels, by: { $0.categoryId ?? "" })
            categories = cats
                .filter { !($0.hidden ?? false) }
                .sorted { ($0.sortOrder ?? 0) < ($1.sortOrder ?? 0) }
                .map { row in
                    CategorySection(
                        id: row.id,
                        name: row.name,
                        sortOrder: row.sortOrder ?? 0,
                        channels: (grouped[row.id] ?? []).sorted { ($0.sortOrder ?? 0) < ($1.sortOrder ?? 0) }
                    )
                }

            selectedCategoryID = selectedCategoryID ?? categories.first?.id

            let groupedSubs = Dictionary(grouping: subs.filter { !($0.hidden ?? false) }, by: { $0.sideMenuId })
            sideMenus = Dictionary(uniqueKeysWithValues: menus.map { menu in
                let items = (groupedSubs[menu.id] ?? []).sorted { ($0.sortOrder ?? 0) < ($1.sortOrder ?? 0) }.map { $0.asChannel() }
                return (menu.id, items)
            })
            sideMenuPins = Dictionary(uniqueKeysWithValues: menus.compactMap { menu in
                if let pin = menu.pinCode, !pin.isEmpty { return (menu.id, pin) }
                return nil
            })

            // 2) Per-channel cache reconcile — replaces ONLY the channels
            //    whose cache_version bumped in the panel, leaves the rest alone.
            SecureCacheManager.reconcile(remote: visibleChannels)
            let groupedSubsForCache = Dictionary(grouping: subs.filter { !($0.hidden ?? false) }, by: { $0.sideMenuId })
                .mapValues { list in list.map { $0.asChannel() } }
            SecureCacheManager.reconcileSubChannels(remote: groupedSubsForCache)

            if gateConfig.enabled && storedBypassCode() != gateConfig.bypassCode {
                launchState = .gate
            } else {
                launchState = .ready
            }
        } catch {
            errorMessage = error.localizedDescription
            // Offline mode: if we have cached content, stay on .ready so the
            // user can still browse categories + images + channels.
            if case .loading = launchState {
                launchState = cachedChannels.isEmpty ? .ready : .ready
            }
        }
    }

    func verifyGate(code: String) -> Bool {
        guard !gateConfig.bypassCode.isEmpty, code == gateConfig.bypassCode else { return false }
        UserDefaults.standard.set(code, forKey: bypassKey)
        launchState = .ready
        return true
    }

    func clearGateBypass() {
        UserDefaults.standard.removeObject(forKey: bypassKey)
    }

    func channelsForSelectedCategory() -> [Channel] {
        categories.first(where: { $0.id == selectedCategoryID })?.channels ?? []
    }

    func subChannels(for menuID: String) -> [Channel] {
        sideMenus[menuID] ?? []
    }

    func sideMenuPin(_ menuID: String) -> String? { sideMenuPins[menuID] }

    func isMenuUnlocked(_ menuID: String) -> Bool { unlockedMenus.contains(menuID) }

    func unlockMenu(_ menuID: String) { unlockedMenus.insert(menuID) }

    func allSearchResults() -> [Channel] {
        let allMain = categories.flatMap(\.channels)
        let allSub = sideMenus.values.flatMap { $0 }
        let all = Array(Set(allMain + allSub)).sorted { $0.name < $1.name }
        guard !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        let query = searchQuery.lowercased()
        return all.filter { $0.name.lowercased().contains(query) }
    }

    private func storedBypassCode() -> String {
        UserDefaults.standard.string(forKey: bypassKey) ?? ""
    }
}

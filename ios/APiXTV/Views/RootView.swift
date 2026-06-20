import SwiftUI
import AVKit
import WebKit

struct RootView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var navigationPath: [Route] = []
    @State private var playerChannel: Channel?
    @State private var showingSearch = false
    @State private var pinPrompt: PinPromptContext?

    var body: some View {
        Group {
            switch viewModel.launchState {
            case .loading:
                // Branded splash so the launch experience matches Android & Windows.
                SplashView()
            case .gate:
                GateView { code in
                    _ = viewModel.verifyGate(code: code)
                }
            case .blocked(let verdict):
                BlockedView(verdict: verdict)
            case .ready:
                NavigationStack(path: $navigationPath) {
                    MainHomeView(
                        categories: viewModel.categories,
                        selectedCategoryID: viewModel.selectedCategoryID,
                        channels: viewModel.channelsForSelectedCategory(),
                        showSettingsSection: viewModel.appSettings.showSettingsSection,
                        onSelectCategory: { viewModel.selectedCategoryID = $0 },
                        onSelectChannel: handleChannelTap,
                        onOpenSearch: { showingSearch = true },
                        onOpenSettings: { navigationPath.append(.settings) }
                    )
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .submenu(let title, let menuID):
                            SubChannelsView(title: title, channels: viewModel.subChannels(for: menuID), onSelectChannel: handleChannelTap)
                        case .settings:
                            SettingsView(telegramURL: viewModel.gateConfig.telegramURL)
                        }
                    }
                    .sheet(isPresented: $showingSearch) {
                        SearchView(query: $viewModel.searchQuery, channels: viewModel.allSearchResults(), onSelectChannel: { ch in
                            showingSearch = false
                            handleChannelTap(ch)
                        })
                    }
                    .fullScreenCover(item: $playerChannel) { channel in
                        if channel.usesWebView {
                            WebPlayerView(channel: channel)
                        } else {
                            NativePlayerView(channel: channel)
                        }
                    }
                    .sheet(item: $pinPrompt) { ctx in
                        PinPromptView(
                            title: ctx.title,
                            expected: ctx.expectedPin,
                            onSuccess: {
                                pinPrompt = nil
                                ctx.onUnlock()
                            },
                            onCancel: { pinPrompt = nil }
                        )
                    }
                }
            }
        }
        .task { await viewModel.bootstrap() }
    }

    private func handleChannelTap(_ channel: Channel) {
        // PIN protection on the channel itself
        if let pin = channel.pinCode, !pin.isEmpty {
            pinPrompt = PinPromptContext(title: channel.name, expectedPin: pin) { [channel] in
                openChannel(channel)
            }
            return
        }
        openChannel(channel)
    }

    private func openChannel(_ channel: Channel) {
        // iOS-specific action type wins over the generic actionType.
        let action = (channel.iosActionType?.lowercased()).flatMap { $0.isEmpty ? nil : $0 }
            ?? channel.actionType
            ?? "direct_play"

        switch action {
        case "external", "external_link":
            let target = channel.externalUrl ?? channel.playbackURL
            if let value = target, let url = URL(string: value) {
                UIApplication.shared.open(url)
            }
        case "open_submenu":
            if let menuID = channel.sideMenuId {
                openSubmenu(title: channel.name, menuID: menuID)
            }
        default:    // direct_play / native / webview — picks player via Channel.usesWebView
            playerChannel = channel
        }
    }

    private func openSubmenu(title: String, menuID: String) {
        if let pin = viewModel.sideMenuPin(menuID),
           !pin.isEmpty,
           !viewModel.isMenuUnlocked(menuID) {
            pinPrompt = PinPromptContext(title: title, expectedPin: pin) { [menuID, title] in
                viewModel.unlockMenu(menuID)
                navigationPath.append(.submenu(title: title, menuID: menuID))
            }
            return
        }
        navigationPath.append(.submenu(title: title, menuID: menuID))
    }
}

enum Route: Hashable {
    case submenu(title: String, menuID: String)
    case settings
}

struct PinPromptContext: Identifiable {
    let id = UUID()
    let title: String
    let expectedPin: String
    let onUnlock: () -> Void
}

struct PinPromptView: View {
    let title: String
    let expected: String
    let onSuccess: () -> Void
    let onCancel: () -> Void
    @State private var entry = ""
    @State private var error: String?

    var body: some View {
        VStack(spacing: 16) {
            Text("هذه القناة محمية برمز")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
            Text(title)
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(AppTheme.gold)
                .multilineTextAlignment(.center)
            SecureField("أدخل الرمز", text: $entry)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .padding(14)
                .background(AppTheme.surface)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 20)
            if let error {
                Text(error).foregroundStyle(.red).font(.caption)
            }
            HStack(spacing: 12) {
                Button("إلغاء", action: onCancel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(AppTheme.card)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                Button("فتح") {
                    if entry == expected {
                        onSuccess()
                    } else {
                        error = "رمز غير صحيح"
                        entry = ""
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(AppTheme.gold)
                .foregroundStyle(.black)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal, 20)
        }
        .padding(.vertical, 28)
        .background(AppTheme.background.ignoresSafeArea())
        .presentationDetents([.medium])
    }
}

struct MainHomeView: View {
    let categories: [CategorySection]
    let selectedCategoryID: String?
    let channels: [Channel]
    let showSettingsSection: Bool
    let onSelectCategory: (String) -> Void
    let onSelectChannel: (Channel) -> Void
    let onOpenSearch: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.horizontalSizeClass) private var hSize

    var body: some View {
        if hSize == .regular {
            iPadLayout
        } else {
            phoneLayout
        }
    }

    // ── iPad / Landscape layout (مطابق للأندرويد Landscape) ──────────
    private var iPadLayout: some View {
        HStack(spacing: 0) {
            // القائمة الجانبية
            VStack(alignment: .center, spacing: 24) {
                VStack(spacing: 2) {
                    Rectangle()
                        .fill(AppTheme.gold)
                        .frame(width: 4, height: 36)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    Text("APiX")
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundStyle(.white)
                    Text("TV")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(AppTheme.gold)
                }
                .padding(.top, 24)

                Divider().background(AppTheme.muted.opacity(0.3))

                ScrollView {
                    VStack(spacing: 6) {
                        ForEach(categories) { cat in
                            iPadCatRow(cat)
                        }
                        if showSettingsSection {
                            Button(action: onOpenSettings) {
                                HStack {
                                    Image(systemName: "gearshape.fill")
                                        .foregroundStyle(AppTheme.muted)
                                    Text("الإعدادات")
                                        .foregroundStyle(AppTheme.muted)
                                        .font(.system(size: 14, weight: .semibold))
                                    Spacer()
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                            }
                        }
                    }
                    .padding(.horizontal, 8)
                }
                Spacer()
            }
            .frame(width: 200)
            .background(Color(red: 0.07, green: 0.07, blue: 0.07))

            // المحتوى الرئيسي
            VStack(spacing: 0) {
                HStack {
                    if let name = categories.first(where: { $0.id == selectedCategoryID })?.name {
                        Text(name.uppercased())
                            .font(.system(size: 20, weight: .heavy))
                            .foregroundStyle(.white)
                    }
                    Spacer()
                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(.white)
                            .font(.system(size: 20, weight: .bold))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)

                let cols = gridColumns(hSize: hSize)
                ScrollView {
                    LazyVGrid(columns: cols, spacing: 12) {
                        ForEach(channels) { ch in
                            ChannelCardView(channel: ch, onTap: { onSelectChannel(ch) })
                        }
                    }
                    .padding(16)
                }
            }
            .background(AppTheme.background)
        }
        .background(AppTheme.background.ignoresSafeArea())
    }

    private func iPadCatRow(_ cat: CategorySection) -> some View {
        let isSelected = cat.id == selectedCategoryID
        return Button { onSelectCategory(cat.id) } label: {
            HStack {
                if isSelected {
                    Rectangle()
                        .fill(AppTheme.gold)
                        .frame(width: 3, height: 20)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                }
                Text(cat.name)
                    .font(.system(size: 14, weight: isSelected ? .bold : .regular))
                    .foregroundStyle(isSelected ? AppTheme.gold : .white)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(isSelected ? AppTheme.gold.opacity(0.12) : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private func gridColumns(hSize: UserInterfaceSizeClass?) -> [GridItem] {
        let count = hSize == .regular ? 4 : 2
        return Array(repeating: GridItem(.flexible(), spacing: 10), count: count)
    }

    // ── iPhone layout (نفس التصميم الحالي) ──────────────────────────
    private var phoneLayout: some View {
        VStack(spacing: 0) {
            HStack {
                Text("APiX TV")
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer()
                Button(action: onOpenSearch) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.white)
                        .font(.system(size: 20, weight: .bold))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if let title = categories.first(where: { $0.id == selectedCategoryID })?.name {
                HStack {
                    Text(title.uppercased())
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundStyle(.white)
                    Spacer()
                }
                .padding(.horizontal, 16)
            }

            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(channels) { channel in
                        ChannelCardView(channel: channel, onTap: { onSelectChannel(channel) })
                    }
                }
                .padding(12)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories) { category in
                        CategoryChip(title: category.name, isSelected: category.id == selectedCategoryID) {
                            onSelectCategory(category.id)
                        }
                    }
                    if showSettingsSection {
                        CategoryChip(title: "الإعدادات", isSelected: false, action: onOpenSettings)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .background(AppTheme.surface)
        }
        .background(AppTheme.background.ignoresSafeArea())
    }
}

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(isSelected ? .black : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isSelected ? AppTheme.gold : AppTheme.card)
                .clipShape(Capsule())
        }
    }
}

struct ChannelCardView: View {
    let channel: Channel
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: channel.imageUrl ?? "")) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Rectangle().fill(AppTheme.card)
                }
                .frame(height: 108)
                .frame(maxWidth: .infinity)
                .clipped()

                LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .center, endPoint: .bottom)
                Text(channel.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(10)
            }
            .background(AppTheme.card)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.gold.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct SubChannelsView: View {
    let title: String
    let channels: [Channel]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(channels) { channel in
                    ChannelCardView(channel: channel, onTap: { onSelectChannel(channel) })
                }
            }
            .padding(12)
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .background(AppTheme.background.ignoresSafeArea())
    }
}

struct SearchView: View {
    @Binding var query: String
    let channels: [Channel]
    let onSelectChannel: (Channel) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                TextField("ابحث عن قناة...", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(14)
                    .background(AppTheme.surface)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        ForEach(channels) { channel in
                            ChannelCardView(channel: channel) {
                                dismiss()
                                onSelectChannel(channel)
                            }
                        }
                    }
                    .padding(12)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("إغلاق") { dismiss() }
                        .foregroundStyle(AppTheme.gold)
                }
            }
            .background(AppTheme.background.ignoresSafeArea())
        }
    }
}

struct SettingsView: View {
    let telegramURL: String

    var body: some View {
        VStack(spacing: 16) {
            SettingsRow(title: "Telegram Channel", url: telegramURL)
            SettingsRow(title: "Contact Support", url: telegramURL)
            Spacer()
        }
        .padding(20)
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("SETTINGS")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SettingsRow: View {
    let title: String
    let url: String

    var body: some View {
        Button {
            if let link = URL(string: url) { UIApplication.shared.open(link) }
        } label: {
            HStack {
                Text(title)
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.forward")
                    .foregroundStyle(AppTheme.gold)
            }
            .padding(16)
            .background(AppTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

struct GateView: View {
    let onSubmitCode: (String) -> Void
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var name = ""
    @State private var link = ""
    @State private var userAgent = ""
    @State private var referer = ""
    @State private var clearKey = ""
    @State private var errorMessage: String?
    @State private var previewChannel: Channel?

    var body: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 20)
            Text(viewModel.gateConfig.title)
                .font(.system(size: 32, weight: .heavy))
                .foregroundStyle(.white)
            if !viewModel.gateConfig.subtitle.isEmpty {
                Text(viewModel.gateConfig.subtitle)
                    .foregroundStyle(AppTheme.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
            Group {
                GateField(title: "اسم القناة", text: $name)
                GateField(title: "رابط القناة", text: $link)
                GateField(title: "User Agent", text: $userAgent)
                GateField(title: "Referrer", text: $referer)
                GateField(title: "ClearKey", text: $clearKey)
            }
            .padding(.horizontal, 18)

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
            }

            Button {
                handlePrimaryAction()
            } label: {
                Text("تشغيل")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(AppTheme.gold)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 18)

            Button("Telegram") {
                if let url = URL(string: viewModel.gateConfig.telegramURL) {
                    UIApplication.shared.open(url)
                }
            }
            .foregroundStyle(AppTheme.gold)

            Spacer()
        }
        .background(AppTheme.background.ignoresSafeArea())
        .fullScreenCover(item: $previewChannel) { channel in
            if channel.usesWebView {
                WebPlayerView(channel: channel)
            } else {
                NativePlayerView(channel: channel)
            }
        }
    }

    private func handlePrimaryAction() {
        let candidateCode = !link.isEmpty ? link : name
        if !viewModel.gateConfig.bypassCode.isEmpty && candidateCode == viewModel.gateConfig.bypassCode {
            onSubmitCode(candidateCode)
            return
        }
        guard !link.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "أدخل رابط أو كود الدخول"
            return
        }
        errorMessage = nil
        let drm: DRMConfig?
        if clearKey.contains(":") {
            let parts = clearKey.split(separator: ":", maxSplits: 1).map(String.init)
            drm = DRMConfig(clearKeyId: parts.first, clearKeyKey: parts.count > 1 ? parts[1] : nil, clearKeyCombined: clearKey, clearKeyUrl: nil, clearKeyMode: "combined")
        } else {
            drm = nil
        }
        previewChannel = Channel(
            id: UUID().uuidString,
            name: name.isEmpty ? "بث يدوي" : name,
            imageUrl: nil,
            sortOrder: 0,
            actionType: "direct_play",
            hidden: false,
            sideMenuId: nil,
            externalUrl: nil,
            preferredPlayer: nil,
            iosActionType: link.lowercased().contains(".mpd") ? "webview" : "native",
            webStream: StreamConfig(url: link, userAgent: userAgent.nilIfEmpty, referrer: referer.nilIfEmpty, cookies: nil, origin: nil, drm: drm),
            androidStream: nil,
            iosStream: nil,
            androidActionType: nil,
            categoryId: nil,
            pinCode: nil,
            offlineCacheEnabled: false,
            cacheVersion: nil,
            forcedAspectRatio: nil,
            lockAspectRatio: nil
        )
    }
}

struct GateField: View {
    let title: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .foregroundStyle(.white)
                .font(.system(size: 14, weight: .semibold))
            TextField(title, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(14)
                .background(AppTheme.surface)
                .foregroundStyle(.white)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.gold.opacity(0.35), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

struct BlockedView: View {
    let verdict: BanVerdict

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text(verdict.message ?? "تم حظر هذا الجهاز")
                .font(.system(size: 24, weight: .heavy))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Text(KeychainDeviceID.get())
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(AppTheme.gold)
                .padding(12)
                .background(AppTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            Button("نسخ ID الحظر") {
                UIPasteboard.general.string = KeychainDeviceID.get()
            }
            .foregroundStyle(.black)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(AppTheme.gold)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            if let link = verdict.telegram_url, let url = URL(string: link) {
                Button("الدعم") { UIApplication.shared.open(url) }
                    .foregroundStyle(AppTheme.gold)
            }
            Spacer()
        }
        .background(AppTheme.background.ignoresSafeArea())
    }
}


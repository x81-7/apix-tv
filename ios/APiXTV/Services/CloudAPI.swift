import Foundation

final class CloudAPI {
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    /// Persisted ETag for the cached-data bundle (so 304s work across launches).
    private static let etagKey = "ios_cached_data_etag"

    private func request(path: String) async throws -> Data {
        var request = URLRequest(url: CloudConfig.baseURL.appending(path: path))
        request.setValue(CloudConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(CloudConfig.anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw URLError(.badServerResponse)
        }
        return data
    }

    // MARK: - Bundle (cached-data edge function)

    struct Bundle {
        let categories: [CategoryRow]
        let channels: [Channel]
        let sideMenus: [SideMenu]
        let subChannels: [SubChannel]
        /// True when the server returned 304 Not Modified — caller should use
        /// the locally cached bundle instead of re-decoding.
        let notModified: Bool
    }

    /// Fetches the aggregated bundle from the `cached-data` Edge Function with
    /// If-None-Match support. Returns `nil` on transport failure so callers can
    /// fall back to the per-table REST endpoints.
    func fetchBundle() async -> Bundle? {
        let url = CloudConfig.baseURL.appending(path: "/functions/v1/cached-data")
        var request = URLRequest(url: url)
        request.setValue(CloudConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(CloudConfig.anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let prevEtag = UserDefaults.standard.string(forKey: Self.etagKey) {
            request.setValue(prevEtag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { return nil }
            if let newEtag = http.value(forHTTPHeaderField: "ETag") {
                UserDefaults.standard.set(newEtag, forKey: Self.etagKey)
            }
            if http.statusCode == 304 {
                return Bundle(categories: [], channels: [], sideMenus: [], subChannels: [], notModified: true)
            }
            guard 200..<300 ~= http.statusCode else { return nil }
            struct Wrapper: Decodable {
                let categories: [CategoryRow]
                let channels: [Channel]
                let sideMenus: [SideMenu]
                let subChannels: [SubChannel]
                // No explicit CodingKeys — rely on `convertFromSnakeCase` so
                // server keys "side_menus" / "sub_channels" map automatically
                // to camelCase. (Mixing explicit raw values with the snake-case
                // strategy silently breaks decoding.)
            }
            let w = try decoder.decode(Wrapper.self, from: data)
            return Bundle(
                categories: w.categories,
                channels: w.channels,
                sideMenus: w.sideMenus,
                subChannels: w.subChannels,
                notModified: false,
            )
        } catch {
            #if DEBUG
            print("CloudAPI.fetchBundle decode error:", error)
            #endif
            return nil
        }
    }

    func fetchCategories() async throws -> [CategoryRow] {
        let data = try await request(path: "/rest/v1/categories?select=id,name,sort_order,hidden&order=sort_order.asc")
        return try decoder.decode([CategoryRow].self, from: data)
    }

    func fetchChannels() async throws -> [Channel] {
        let data = try await request(path: "/rest/v1/channels?select=*,cache_version,ios_stream,ios_action_type,pin_code,offline_cache_enabled&order=sort_order.asc")
        return try decoder.decode([Channel].self, from: data)
    }

    func fetchSideMenus() async throws -> [SideMenu] {
        let data = try await request(path: "/rest/v1/side_menus?select=id,name,sort_order,pin_code&order=sort_order.asc")
        return try decoder.decode([SideMenu].self, from: data)
    }

    func fetchSubChannels() async throws -> [SubChannel] {
        let data = try await request(path: "/rest/v1/sub_channels?select=*,cache_version,ios_stream,ios_action_type,pin_code,offline_cache_enabled&order=sort_order.asc")
        return try decoder.decode([SubChannel].self, from: data)
    }

    func fetchAppSettings() async -> AppSettings {
        guard let value = try? await fetchSettingValue(key: "appSettings") as? [String: Any] else { return AppSettings() }
        return AppSettings(showSettingsSection: value["showSettingsSection"] as? Bool ?? true)
    }

    func fetchGateConfig() async -> GateConfig {
        guard let value = try? await fetchSettingValue(key: "gateConfig") as? [String: Any] else { return GateConfig() }
        return GateConfig(
            enabled: value["enabled"] as? Bool ?? false,
            title: value["title"] as? String ?? "APiX TV",
            subtitle: value["subtitle"] as? String ?? "",
            telegramURL: value["telegramUrl"] as? String ?? "https://t.me/apix_tv",
            bypassCode: value["bypassCode"] as? String ?? ""
        )
    }

    private func fetchSettingValue(key: String) async throws -> Any? {
        let safeKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
        let data = try await request(path: "/rest/v1/system_settings?key=eq.\(safeKey)&select=value")
        guard let raw = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
              let first = raw.first else { return nil }
        return first["value"]
    }
}

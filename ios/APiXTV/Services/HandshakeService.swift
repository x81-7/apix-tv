import Foundation

final class HandshakeService {
    func validateCurrentDevice() async -> BanVerdict {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let body: [String: Any] = [
            "device_id": KeychainDeviceID.get(),
            "platform": "ios",
            "app_version": version,
            "is_fresh_install": false
        ]

        var request = URLRequest(url: CloudConfig.baseURL.appending(path: "/functions/v1/device-handshake"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(CloudConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(CloudConfig.anonKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
                return BanVerdict(status: "ERROR", ban_until: nil, ban_reason: nil, telegram_url: nil, message: "Network error")
            }
            return try JSONDecoder().decode(BanVerdict.self, from: data)
        } catch {
            return BanVerdict(status: "ERROR", ban_until: nil, ban_reason: nil, telegram_url: nil, message: error.localizedDescription)
        }
    }
}

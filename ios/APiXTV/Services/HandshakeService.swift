import Foundation

final class HandshakeService {
    func validateCurrentDevice() async -> BanVerdict {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        var body: [String: Any] = [
            "device_id": KeychainDeviceID.get(),
            "platform": "ios",
            "app_version": version,
            "is_fresh_install": false
        ]
        // iOS environment-danger probe (Jailbreak / debugger) — server takes
        // the same shape as the Android client.
        if let danger = DeviceIntegrityIOS.environmentDanger() {
            body["environment_danger"] = true
            body["danger_details"] = danger
        }

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
            // Server now returns AES-GCM envelope { iv, data } for client calls.
            // Detect & decrypt; fall back to plain JSON for older deployments.
            let payload = decryptIfEnvelope(data)
            return try JSONDecoder().decode(BanVerdict.self, from: payload)
        } catch {
            return BanVerdict(status: "ERROR", ban_until: nil, ban_reason: nil, telegram_url: nil, message: error.localizedDescription)
        }
    }

    private func decryptIfEnvelope(_ data: Data) -> Data {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              obj["iv"] is String, obj["data"] is String else {
            return data
        }
        if let plain = try? PayloadCipher.decrypt(envelope: data) {
            return plain
        }
        return data
    }
}

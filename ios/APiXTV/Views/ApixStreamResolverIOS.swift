import Foundation
import CryptoKit

struct ApixResolvedConfig {
    var url: String
    var backupUrl: String?
    var headers: [String: String]
    var clearKey: String?
    var subtitleUrl: String?
    var title: String?
    var player: String
}

enum ApixStreamResolverIOS {

    static func isApixStream(_ urlString: String) -> Bool {
        let lower = urlString.lowercased().trimmingCharacters(in: .whitespaces)
        return lower.hasSuffix("apix.png")
            || lower.contains("/apix.png?")
            || lower.contains("/apix.png#")
    }

    static func resolve(_ urlString: String) async -> ApixResolvedConfig? {
        guard let url = URL(string: urlString) else { return nil }
        do {
            var request = URLRequest(url: url, timeoutInterval: 15)
            request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            request.setValue("application/json, image/png, */*", forHTTPHeaderField: "Accept")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let body = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
            else { return nil }
            
            // تم التعديل هنا إلى decrypt بدلاً من decryptExternal
            let plain = try PayloadCipher.decrypt(body)
            
            return parseJson(plain)
        } catch {
            return nil
        }
    }

    private static func parseJson(_ json: String) -> ApixResolvedConfig? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streamUrl = obj["url"] as? String, !streamUrl.isEmpty
        else { return nil }

        var headers: [String: String] = [:]
        if let h = obj["headers"] as? [String: String] { headers.merge(h) { _, new in new } }
        if let ch = obj["customHeaders"] as? [String: String] { headers.merge(ch) { _, new in new } }

        var clearKey: String?
        if let drm = obj["drm"] as? [String: String] {
            let keyId = drm["keyId"] ?? ""
            let key   = drm["key"]   ?? ""
            if !keyId.isEmpty && !key.isEmpty { clearKey = "\(keyId):\(key)" }
        }

        return ApixResolvedConfig(
            url:         streamUrl,
            backupUrl:   obj["backupUrl"]   as? String,
            headers:     headers,
            clearKey:    clearKey,
            subtitleUrl: obj["subtitleUrl"] as? String,
            title:       obj["name"]        as? String,
            player:      (obj["player"]     as? String) ?? "native"
        )
    }
}

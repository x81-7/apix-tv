import Foundation

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
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15",
                             forHTTPHeaderField: "User-Agent")
            request.setValue("application/json, image/png, */*", forHTTPHeaderField: "Accept")

            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let body = String(data: data, encoding: .utf8)?
                                .trimmingCharacters(in: .whitespacesAndNewlines),
                  !body.isEmpty
            else { return nil }

            // فك التشفير بالمفتاح الخارجي
            let plain = try PayloadCipher.decryptExternal(envelope: body)
            return await parseJson(plain)

        } catch {
            return nil
        }
    }

    private static func parseJson(_ json: String) async -> ApixResolvedConfig? {
        guard let data = json.data(using: .utf8),
              let obj  = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }

        // فحص نوع البيانات — OK.ru extractor
        if (obj["type"] as? String) == "okru_extractor" {
            let videoId   = obj["videoId"]   as? String ?? ""
            let cookie    = obj["cookie"]    as? String ?? ""
            let tkn       = obj["tkn"]       as? String ?? ""
            let userAgent = obj["userAgent"] as? String
                ?? "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"

            guard let extracted = await extractOkRu(
                videoId: videoId, cookie: cookie, tkn: tkn, userAgent: userAgent
            ) else { return nil }

            return ApixResolvedConfig(
                url:        extracted,
                backupUrl:  nil,
                headers:    [
                    "User-Agent": userAgent,
                    "Referer":    "https://ok.ru/video/\(videoId)",
                    "Origin":     "https://ok.ru"
                ],
                clearKey:    nil,
                subtitleUrl: nil,
                title:       "OK.ru Stream",
                player:      "native"
            )
        }

        // رابط عادي
        guard let streamUrl = obj["url"] as? String, !streamUrl.isEmpty else { return nil }

        var headers: [String: String] = [:]
        if let h  = obj["headers"]       as? [String: String] { headers.merge(h)  { _, n in n } }
        if let ch = obj["customHeaders"] as? [String: String] { headers.merge(ch) { _, n in n } }

        var clearKey: String?
        if let drm = obj["drm"] as? [String: String] {
            let kid = drm["keyId"] ?? ""; let key = drm["key"] ?? ""
            if !kid.isEmpty && !key.isEmpty { clearKey = "\(kid):\(key)" }
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

    private static func extractOkRu(
        videoId: String, cookie: String, tkn: String, userAgent: String
    ) async -> String? {
        guard let url = URL(string: "https://ok.ru/dk?cmd=videoPlayerMetadata") else { return nil }
        var req = URLRequest(url: url, timeoutInterval: 15)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.setValue(userAgent,                   forHTTPHeaderField: "User-Agent")
        req.setValue(cookie,                      forHTTPHeaderField: "Cookie")
        req.setValue("https://ok.ru/video/\(videoId)", forHTTPHeaderField: "Referer")
        req.setValue("https://ok.ru",             forHTTPHeaderField: "Origin")
        req.setValue("application/json, */*",     forHTTPHeaderField: "Accept")
        req.httpBody = "mid=\(videoId)&tkn=\(tkn)&is=on".data(using: .utf8)

        do {
            let (data, res) = try await URLSession.shared.data(for: req)
            guard (res as? HTTPURLResponse)?.statusCode == 200,
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            else { return nil }

            let clean: (String) -> String = { $0.replacingOccurrences(of: "\\u0026", with: "&") }
            if let hls = obj["hlsMasterPlaylistUrl"] as? String, !hls.isEmpty { return clean(hls) }
            if let hls = obj["hlsManifestUrl"]       as? String, !hls.isEmpty { return clean(hls) }

            if let videos = obj["videos"] as? [[String: Any]] {
                var map: [String: String] = [:]
                for v in videos {
                    if let u = v["url"] as? String, let r = v["name"] as? String,
                       !u.isEmpty, !r.isEmpty { map[r] = u }
                }
                for p in ["1080", "720", "480", "360", "240"] {
                    if let u = map[p] { return u }
                }
                return map.values.first
            }
        } catch {}
        return nil
    }
}
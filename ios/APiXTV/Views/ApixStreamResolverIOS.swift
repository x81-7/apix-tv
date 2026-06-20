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
            
            // فك التشفير
            let plain = try PayloadCipher.decrypt(envelope: body)
            
            // تحويل النص إلى قاموس وتحديد نوعه
            return await parseJson(plain)
        } catch {
            return nil
        }
    }

    private static func parseJson(_ json: String) async -> ApixResolvedConfig? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }

        // 1. فحص إذا كان الاستخراج من موقع OK.ru
        if let type = obj["type"] as? String, type == "okru_extractor" {
            let videoId = obj["videoId"] as? String ?? ""
            let cookie = obj["cookie"] as? String ?? ""
            let tkn = obj["tkn"] as? String ?? ""
            let userAgent = obj["userAgent"] as? String ?? "Mozilla/5.0 (Linux; Android 12)"
            
            // مناداة دالة الاستخراج لجلب الرابط الفعلي
            if let extractedUrl = await extractOkRu(videoId: videoId, cookie: cookie, tkn: tkn, userAgent: userAgent) {
                return ApixResolvedConfig(
                    url: extractedUrl,
                    backupUrl: nil,
                    headers: [
                        "User-Agent": userAgent,
                        "Referer": "https://ok.ru/video/\(videoId)",
                        "Origin": "https://ok.ru"
                    ],
                    clearKey: nil,
                    subtitleUrl: nil,
                    title: "OK.ru Stream",
                    player: "native"
                )
            } else {
                return nil // فشل الاستخراج من الموقع الروسي
            }
        }

        // 2. القراءة العادية إذا لم يكن من الموقع الروسي
        guard let streamUrl = obj["url"] as? String, !streamUrl.isEmpty else { return nil }

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
    
    // MARK: - دالة استخراج الروابط من OK.ru (مترجمة من الأندرويد)
    private static func extractOkRu(videoId: String, cookie: String, tkn: String, userAgent: String) async -> String? {
        guard let url = URL(string: "https://ok.ru/dk?cmd=videoPlayerMetadata") else { return nil }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 15.0
        
        // إعداد الهيدر بناءً على كود الأندرويد
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(cookie, forHTTPHeaderField: "Cookie")
        request.setValue(tkn, forHTTPHeaderField: "tkn")
        request.setValue("https://ok.ru/video/\(videoId)", forHTTPHeaderField: "Referer")
        request.setValue("https://ok.ru", forHTTPHeaderField: "Origin")
        request.setValue("application/json, */*", forHTTPHeaderField: "Accept")
        request.setValue("ar,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        
        // إعداد الـ Body
        let bodyString = "mid=\(videoId)&is=on"
        request.httpBody = bodyString.data(using: .utf8)
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return nil
            }
            
            guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            
            // 1. فحص رابط البث المباشر HLS
            if let hlsUrl = obj["hlsMasterPlaylistUrl"] as? String, !hlsUrl.isEmpty {
                return hlsUrl.replacingOccurrences(of: "\\u0026", with: "&")
            }
            if let hlsUrl = obj["hlsManifestUrl"] as? String, !hlsUrl.isEmpty {
                return hlsUrl.replacingOccurrences(of: "\\u0026", with: "&")
            }
            
            // 2. خطة بديلة (Fallback) للفيديو المسجل
            if let videos = obj["videos"] as? [[String: Any]] {
                var map: [String: String] = [:]
                for v in videos {
                    if let url = v["url"] as? String, let res = v["name"] as? String, !url.isEmpty, !res.isEmpty {
                        map[res] = url
                    }
                }
                
                let priority = ["1080", "720", "480", "360", "240"]
                for p in priority {
                    if let targetUrl = map[p] { return targetUrl }
                }
                
                // جلب أول رابط متوفر في حال عدم توفر الجودات المطلوبة
                return map.values.first
            }
            
            return nil
        } catch {
            return nil
        }
    }
}

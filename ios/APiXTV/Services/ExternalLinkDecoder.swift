import Foundation
import CryptoKit

/// Decodes external `apix://<payload>` and `https://apix-panal.vercel.app/watch.html?id=<payload>`
/// links into a `ExternalStream` description. Mirrors the Android `DataProcessor` exactly.
struct ExternalStream {
    let url: String
    let title: String
    let player: String        // "exoplayer" | "shaka" | "webview" | "jw"
    let userAgent: String?
    let referer: String?
    let customHeaders: [String: String]
    let drmScheme: String?
    let drmKeyId: String?
    let drmKey: String?
    let drmLicenseUrl: String?
}

enum ExternalLinkDecoder {

    /// AES-256 hex key. Must match the panel generator and Android `EXTERNAL_PANEL_DECRYPTION_KEY`.
    private static let keyHex = "7a3f8b9d4e2c1a5f6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a"

    static func extractPayload(from url: URL) -> String? {
        let scheme = url.scheme?.lowercased() ?? ""
        if scheme == "apix" {
            // apix://<payload>  → host
            if let h = url.host, !h.isEmpty { return h }
            // apix:///<payload>  → first path segment
            let seg = url.pathComponents.first(where: { $0 != "/" })
            if let s = seg, !s.isEmpty { return s }
        }
        // https://apix-panal.vercel.app/watch.html?id=<payload>
        if let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            for q in comps.queryItems ?? [] {
                if (q.name == "id" || q.name == "payload"), let v = q.value, !v.isEmpty {
                    return v
                }
            }
        }
        return nil
    }

    static func decode(payload: String) -> ExternalStream? {
        guard let raw = base64UrlDecode(payload), raw.count > 12 + 16 else { return nil }
        guard let key = hexToBytes(keyHex), key.count == 32 else { return nil }

        let iv = raw.prefix(12)
        let body = raw.dropFirst(12)
        // Last 16 bytes = GCM auth tag (matches Android default)
        let tag = body.suffix(16)
        let ct  = body.dropLast(16)

        let symKey = SymmetricKey(data: Data(key))
        do {
            let sealed = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: Data(iv)),
                                               ciphertext: Data(ct),
                                               tag: Data(tag))
            let plain = try AES.GCM.open(sealed, using: symKey)
            guard let json = try JSONSerialization.jsonObject(with: plain) as? [String: Any] else { return nil }
            return parse(json)
        } catch {
            return nil
        }
    }

    private static func parse(_ j: [String: Any]) -> ExternalStream? {
        guard let url = j["url"] as? String, !url.isEmpty else { return nil }
        let h = j["headers"] as? [String: Any] ?? [:]
        let ch = j["customHeaders"] as? [String: Any] ?? [:]
        let drm = j["drm"] as? [String: Any]
        var custom: [String: String] = [:]
        for (k, v) in ch { if let s = v as? String { custom[k] = s } }
        return ExternalStream(
            url: url,
            title: (j["name"] as? String) ?? "External",
            player: (j["player"] as? String) ?? "exoplayer",
            userAgent: h["userAgent"] as? String,
            referer: h["referer"] as? String,
            customHeaders: custom,
            drmScheme: drm?["scheme"] as? String,
            drmKeyId: drm?["keyId"] as? String,
            drmKey: drm?["key"] as? String,
            drmLicenseUrl: drm?["licenseUrl"] as? String
        )
    }

    // MARK: - helpers

    private static func base64UrlDecode(_ s: String) -> Data? {
        var t = s.replacingOccurrences(of: "-", with: "+")
                 .replacingOccurrences(of: "_", with: "/")
        while t.count % 4 != 0 { t += "=" }
        return Data(base64Encoded: t)
    }

    private static func hexToBytes(_ s: String) -> [UInt8]? {
        let clean = s.filter { $0.isHexDigit }
        guard clean.count % 2 == 0 else { return nil }
        var out: [UInt8] = []
        out.reserveCapacity(clean.count / 2)
        var i = clean.startIndex
        while i < clean.endIndex {
            let next = clean.index(i, offsetBy: 2)
            guard let b = UInt8(clean[i..<next], radix: 16) else { return nil }
            out.append(b)
            i = next
        }
        return out
    }
}

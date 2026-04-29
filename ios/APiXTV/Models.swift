import Foundation

struct CategoryRow: Codable {
    let id: String
    let name: String
    let sortOrder: Int?
    let hidden: Bool?
}

struct Channel: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let imageUrl: String?
    let sortOrder: Int?
    let actionType: String?
    let hidden: Bool?
    let sideMenuId: String?
    let externalUrl: String?
    let preferredPlayer: String?
    let iosActionType: String?
    let webStream: StreamConfig?
    let androidStream: AndroidStreamConfig?
    let iosStream: IosStreamConfig?
    let androidActionType: String?
    let categoryId: String?
    let pinCode: String?
    let offlineCacheEnabled: Bool?
    let cacheVersion: Int?
    let forcedAspectRatio: String?
    let lockAspectRatio: Bool?

    var playbackURL: String? {
        iosStream?.url ?? webStream?.url ?? androidStream?.url
    }

    var effectiveTitle: String { name }

    var usesWebView: Bool {
        let action = (iosActionType ?? "").lowercased()
        if action == "webview" { return true }
        if action == "native" { return false }
        let url = playbackURL?.lowercased() ?? ""
        // DASH (.mpd) and any clear-key DRM stream cannot use AVPlayer →
        // must fall back to the WebView shaka/dashjs player.
        if url.contains(".mpd") { return true }
        if iosStream?.drmKeyId != nil || iosStream?.drmKey != nil { return true }
        if webStream?.drm?.clearKeyCombined?.isEmpty == false { return true }
        return false
    }

    /// Headers that should be attached to AVURLAsset / WKWebView requests.
    /// iosStream wins when present, falls back to webStream, then androidStream.
    var effectiveHeaders: [String: String] {
        var out: [String: String] = [:]
        if let ua = iosStream?.userAgent ?? webStream?.userAgent, !ua.isEmpty {
            out["User-Agent"] = ua
        }
        if let ref = iosStream?.referrer ?? webStream?.referrer, !ref.isEmpty {
            out["Referer"] = ref
        }
        if let origin = iosStream?.origin ?? webStream?.origin, !origin.isEmpty {
            out["Origin"] = origin
        }
        if let cookies = iosStream?.cookies ?? webStream?.cookies, !cookies.isEmpty {
            out["Cookie"] = cookies
        }
        // Free-form headers map
        if let map = iosStream?.headers {
            for (k, v) in map { if !k.isEmpty { out[k] = v } }
        }
        // Custom headers list (key/value pairs)
        if let list = iosStream?.customHeaders {
            for h in list where !h.key.isEmpty { out[h.key] = h.value }
        }
        return out
    }

    /// Effective backup URL (used by AVPlayer on primary failure).
    var backupURL: URL? {
        if let s = iosStream?.backupUrl, !s.isEmpty, let u = URL(string: s) { return u }
        if let s = androidStream?.backupUrl, !s.isEmpty, let u = URL(string: s) { return u }
        return nil
    }

    /// Subtitles URL.
    var subtitleURL: URL? {
        if let s = iosStream?.subtitleUrl, !s.isEmpty, let u = URL(string: s) { return u }
        if let s = androidStream?.subtitleUrl, !s.isEmpty, let u = URL(string: s) { return u }
        return nil
    }

    /// Combined ClearKey "kid:key" if available from any source.
    var clearKeyCombined: String? {
        if let v = iosStream?.drmClearKey, !v.isEmpty { return v }
        if let v = webStream?.drm?.clearKeyCombined, !v.isEmpty { return v }
        if let v = androidStream?.drmClearKeyCombined, !v.isEmpty { return v }
        if let id = iosStream?.drmKeyId ?? webStream?.drm?.clearKeyId ?? androidStream?.drmKeyId,
           let key = iosStream?.drmKey ?? webStream?.drm?.clearKeyKey ?? androidStream?.drmKey,
           !id.isEmpty, !key.isEmpty {
            return "\(id):\(key)"
        }
        return nil
    }
}

struct SideMenu: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let sortOrder: Int?
    let pinCode: String?
}

struct SubChannel: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let imageUrl: String?
    let sortOrder: Int?
    let hidden: Bool?
    let preferredPlayer: String?
    let webStream: StreamConfig?
    let androidStream: AndroidStreamConfig?
    let iosStream: IosStreamConfig?
    let androidActionType: String?
    let iosActionType: String?
    let sideMenuId: String
    let pinCode: String?
    let offlineCacheEnabled: Bool?
    let cacheVersion: Int?
    let forcedAspectRatio: String?
    let lockAspectRatio: Bool?

    func asChannel() -> Channel {
        Channel(
            id: id,
            name: name,
            imageUrl: imageUrl,
            sortOrder: sortOrder,
            actionType: "direct_play",
            hidden: hidden,
            sideMenuId: sideMenuId,
            externalUrl: nil,
            preferredPlayer: preferredPlayer,
            iosActionType: iosActionType,
            webStream: webStream,
            androidStream: androidStream,
            iosStream: iosStream,
            androidActionType: androidActionType,
            categoryId: nil,
            pinCode: pinCode,
            offlineCacheEnabled: offlineCacheEnabled,
            cacheVersion: cacheVersion,
            forcedAspectRatio: forcedAspectRatio,
            lockAspectRatio: lockAspectRatio
        )
    }
}

struct StreamConfig: Codable, Hashable {
    let url: String?
    let userAgent: String?
    let referrer: String?
    let cookies: String?
    let origin: String?
    let drm: DRMConfig?
}

/// iOS-specific stream configuration. Mirrors AndroidStreamConfig so the
/// dashboard can target each platform separately. Falls back to webStream
/// when not provided.
struct IosStreamConfig: Codable, Hashable {
    let url: String?
    let userAgent: String?
    let referrer: String?
    let origin: String?
    let cookies: String?
    let backupUrl: String?
    let subtitleUrl: String?
    let headers: [String: String]?
    let customHeaders: [CustomHeaderEntry]?
    let drmScheme: String?
    let drmKeyId: String?
    let drmKey: String?
    let drmClearKey: String?
    let drmLicenseUrl: String?
}

struct CustomHeaderEntry: Codable, Hashable {
    let key: String
    let value: String
}

struct AndroidStreamConfig: Codable, Hashable {
    let url: String?
    let webViewOrientation: String?
    let headers: [String: String]?
    let drmLicenseUrl: String?
    let drmScheme: String?
    let drmKeyId: String?
    let drmKey: String?
    let drmClearKeyCombined: String?
    let drmClearKeyMode: String?
    let backupUrl: String?
    let subtitleUrl: String?
}

struct DRMConfig: Codable, Hashable {
    let clearKeyId: String?
    let clearKeyKey: String?
    let clearKeyCombined: String?
    let clearKeyUrl: String?
    let clearKeyMode: String?
}

struct AppSettings {
    var showSettingsSection: Bool = true
}

struct GateConfig {
    var enabled: Bool = false
    var title: String = "APiX TV"
    var subtitle: String = ""
    var telegramURL: String = "https://t.me/apix_tv"
    var bypassCode: String = ""
}

struct BanVerdict: Codable {
    let status: String
    let ban_until: String?
    let ban_reason: String?
    let telegram_url: String?
    let message: String?
}

struct CategorySection: Identifiable, Hashable {
    let id: String
    let name: String
    let sortOrder: Int
    let channels: [Channel]
}

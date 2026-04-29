import Foundation
import Security
import CryptoKit

/// SecureCacheManager — mirrors the Android `SecureCacheManager` behaviour.
///
/// • Stores channels marked `offline_cache_enabled = true` on the device,
///   encrypted with AES-GCM using a 256-bit key kept in the iOS Keychain.
/// • The cache is read first on app launch — Supabase is only contacted to
///   refresh data, dramatically reducing PostgREST egress for repeat opens.
/// • Even a jailbroken device cannot decrypt the JSON without bypassing
///   the Keychain (kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly).
enum SecureCacheManager {

    private static let keyTag = "com.apix.app.secureCache.key"
    private static let cacheURL: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("ApixSecureCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("channels.bin")
    }()

    // MARK: - Public API

    /// Save the cacheable channels JSON. Encrypted at rest.
    static func saveChannels(_ channels: [Channel]) {
        do {
            let data = try JSONEncoder().encode(channels)
            let key = try loadOrCreateKey()
            let sealed = try AES.GCM.seal(data, using: key)
            guard let combined = sealed.combined else { return }
            try combined.write(to: cacheURL, options: .completeFileProtection)
        } catch {
            // Cache failure is non-fatal — app still works via network.
            #if DEBUG
            print("SecureCache save failed: \(error)")
            #endif
        }
    }

    /// Load the cached channels (returns empty if no cache or decrypt fails).
    static func loadChannels() -> [Channel] {
        guard FileManager.default.fileExists(atPath: cacheURL.path) else { return [] }
        do {
            let blob = try Data(contentsOf: cacheURL)
            let key = try loadOrCreateKey()
            let box = try AES.GCM.SealedBox(combined: blob)
            let plain = try AES.GCM.open(box, using: key)
            return try JSONDecoder().decode([Channel].self, from: plain)
        } catch {
            #if DEBUG
            print("SecureCache load failed: \(error)")
            #endif
            return []
        }
    }

    /// Reconciles the local cache against the latest server snapshot.
    /// Only channels whose `cacheVersion` changed — or that aren't cached yet —
    /// are overwritten. Everything else is left alone, so the user only
    /// re-downloads the specific channel the admin edited, not the whole list.
    static func reconcile(remote: [Channel]) {
        let currentById = Dictionary(uniqueKeysWithValues: loadChannels().map { ($0.id, $0) })
        var next: [Channel] = []
        for r in remote where (r.offlineCacheEnabled ?? false) {
            if let local = currentById[r.id],
               (local.cacheVersion ?? -1) == (r.cacheVersion ?? -2) {
                next.append(local)       // still fresh — keep local copy
            } else {
                next.append(r)           // admin edited it → replace
            }
        }
        saveChannels(next)
    }

    /// Same logic for the per-side-menu sub-channels map.
    static func reconcileSubChannels(remote: [String: [Channel]]) {
        let local = loadSubChannels()
        var next: [String: [Channel]] = [:]
        for (menuId, remoteChannels) in remote {
            let localById = Dictionary(uniqueKeysWithValues: (local[menuId] ?? []).map { ($0.id, $0) })
            next[menuId] = remoteChannels.filter { $0.offlineCacheEnabled == true }.map { r in
                if let cached = localById[r.id],
                   (cached.cacheVersion ?? -1) == (r.cacheVersion ?? -2) {
                    return cached
                }
                return r
            }
        }
        saveSubChannels(next)
    }

    /// Cached SubChannels are kept in the same blob keyed by their parent menu.
    static func saveSubChannels(_ map: [String: [Channel]]) {
        do {
            let data = try JSONEncoder().encode(map)
            let key = try loadOrCreateKey()
            let sealed = try AES.GCM.seal(data, using: key)
            guard let combined = sealed.combined else { return }
            let url = cacheURL.deletingLastPathComponent().appendingPathComponent("subchannels.bin")
            try combined.write(to: url, options: .completeFileProtection)
        } catch {
            #if DEBUG
            print("SecureCache subSave failed: \(error)")
            #endif
        }
    }

    static func loadSubChannels() -> [String: [Channel]] {
        let url = cacheURL.deletingLastPathComponent().appendingPathComponent("subchannels.bin")
        guard FileManager.default.fileExists(atPath: url.path) else { return [:] }
        do {
            let blob = try Data(contentsOf: url)
            let key = try loadOrCreateKey()
            let box = try AES.GCM.SealedBox(combined: blob)
            let plain = try AES.GCM.open(box, using: key)
            return try JSONDecoder().decode([String: [Channel]].self, from: plain)
        } catch { return [:] }
    }

    static func clear() {
        try? FileManager.default.removeItem(at: cacheURL)
        let sub = cacheURL.deletingLastPathComponent().appendingPathComponent("subchannels.bin")
        try? FileManager.default.removeItem(at: sub)
    }

    // MARK: - Keychain key management

    private static func loadOrCreateKey() throws -> SymmetricKey {
        if let existing = readKey() { return existing }
        let fresh = SymmetricKey(size: .bits256)
        try storeKey(fresh)
        return fresh
    }

    private static func readKey() -> SymmetricKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keyTag,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return SymmetricKey(data: data)
    }

    private static func storeKey(_ key: SymmetricKey) throws {
        let data = key.withUnsafeBytes { Data($0) }
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keyTag,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data
        ]
        // Delete any existing item first to avoid duplicate errors.
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keyTag
        ] as CFDictionary)
        let status = SecItemAdd(attributes as CFDictionary, nil)
        if status != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }
}
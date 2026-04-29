import Foundation
import CryptoKit

/// AES-256-GCM payload cipher.
///
/// Decrypts the `{ "iv": "<base64>", "data": "<base64>" }` envelope returned
/// by the Supabase `cached-data` Edge Function. The 32-byte key MUST match
/// the server-side `ENCRYPTION_SECRET_KEY` env var.
///
/// Key sourcing order:
///   1. `Info.plist` value for `APIX_ENCRYPTION_KEY`
///   2. `ProcessInfo` env var `ENCRYPTION_SECRET_KEY`
///   3. compile-time placeholder (replaced by CI for production builds)
enum PayloadCipher {

    enum CipherError: Error {
        case missingKey
        case badKey
        case badEnvelope
        case decryptFailed
    }

    /// 32-byte all-zero placeholder. CI MUST inject the real key into
    /// Info.plist (`APIX_ENCRYPTION_KEY`) before archiving.
    private static let fallbackKeyHex =
        "0000000000000000000000000000000000000000000000000000000000000000"

    private static let key: SymmetricKey = {
        let raw: String = {
            if let v = Bundle.main.object(forInfoDictionaryKey: "APIX_ENCRYPTION_KEY") as? String,
               !v.isEmpty {
                return v
            }
            if let v = ProcessInfo.processInfo.environment["ENCRYPTION_SECRET_KEY"],
               !v.isEmpty {
                return v
            }
            return fallbackKeyHex
        }()
        do {
            return SymmetricKey(data: try decodeKey(raw))
        } catch {
            // Falls back to zero-key — decrypt will simply fail at runtime.
            return SymmetricKey(data: Data(repeating: 0, count: 32))
        }
    }()

    private static func decodeKey(_ raw: String) throws -> Data {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw CipherError.missingKey }
        // hex 64 chars
        if trimmed.count == 64,
           trimmed.range(of: "^[0-9a-fA-F]+$", options: .regularExpression) != nil {
            var bytes = [UInt8]()
            bytes.reserveCapacity(32)
            var idx = trimmed.startIndex
            while idx < trimmed.endIndex {
                let next = trimmed.index(idx, offsetBy: 2)
                guard let b = UInt8(trimmed[idx..<next], radix: 16) else { throw CipherError.badKey }
                bytes.append(b)
                idx = next
            }
            return Data(bytes)
        }
        // base64
        guard let d = Data(base64Encoded: trimmed), d.count == 32 else { throw CipherError.badKey }
        return d
    }

    /// Decrypt envelope JSON `Data` -> plaintext `Data` (UTF-8 JSON).
    static func decrypt(envelope: Data) throws -> Data {
        guard let obj = try JSONSerialization.jsonObject(with: envelope) as? [String: Any],
              let ivB64 = obj["iv"] as? String,
              let dataB64 = obj["data"] as? String,
              let iv = Data(base64Encoded: ivB64),
              let ct = Data(base64Encoded: dataB64) else {
            throw CipherError.badEnvelope
        }
        // CryptoKit expects ciphertext + 16-byte tag combined — server emits
        // them concatenated (WebCrypto AES-GCM appends the tag).
        guard ct.count > 16 else { throw CipherError.badEnvelope }
        let tag = ct.suffix(16)
        let cipherText = ct.prefix(ct.count - 16)
        do {
            let nonce = try AES.GCM.Nonce(data: iv)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: cipherText, tag: tag)
            return try AES.GCM.open(box, using: key)
        } catch {
            throw CipherError.decryptFailed
        }
    }
}

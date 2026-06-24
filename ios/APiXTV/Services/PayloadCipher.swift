import Foundation
import CryptoKit

enum PayloadCipher {

    enum CipherError: Error {
        case missingKey, badKey, badEnvelope, decryptFailed
    }

    // ── Internal key (ENCRYPTION_SECRET_KEY) ─────────────────────────
    private static let key: SymmetricKey = buildKey(
        plistKey: "APIX_ENCRYPTION_KEY",
        envKey:   "ENCRYPTION_SECRET_KEY"
    )

    // ── External key (EXTERNAL_PANEL_DECRYPTION_KEY) ──────────────────
    // هذا المفتاح خاص بـ apix.png — يجب حقنه في Info.plist كـ APIX_EXTERNAL_KEY
    private static let externalKey: SymmetricKey = buildKey(
        plistKey: "APIX_EXTERNAL_KEY",
        envKey:   "EXTERNAL_PANEL_DECRYPTION_KEY"
    )

    private static func buildKey(plistKey: String, envKey: String) -> SymmetricKey {
        let raw: String = {
            if let v = Bundle.main.object(forInfoDictionaryKey: plistKey) as? String,
               !v.isEmpty { return v }
            if let v = ProcessInfo.processInfo.environment[envKey],
               !v.isEmpty { return v }
            return "0000000000000000000000000000000000000000000000000000000000000000"
        }()
        do {
            return SymmetricKey(data: try decodeKey(raw))
        } catch {
            return SymmetricKey(data: Data(repeating: 0, count: 32))
        }
    }

    private static func decodeKey(_ raw: String) throws -> Data {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { throw CipherError.missingKey }
        if t.count == 64,
           t.range(of: "^[0-9a-fA-F]+$", options: .regularExpression) != nil {
            var bytes = [UInt8](); bytes.reserveCapacity(32)
            var idx = t.startIndex
            while idx < t.endIndex {
                let next = t.index(idx, offsetBy: 2)
                guard let b = UInt8(t[idx..<next], radix: 16) else { throw CipherError.badKey }
                bytes.append(b); idx = next
            }
            return Data(bytes)
        }
        guard let d = Data(base64Encoded: t), d.count == 32 else { throw CipherError.badKey }
        return d
    }

    // ── فك تشفير بالمفتاح الداخلي (Supabase/Worker) ──────────────────
    static func decrypt(envelope: Data) throws -> Data {
        try gcmDecrypt(envelope: envelope, using: key)
    }

    // ── فك تشفير بالمفتاح الخارجي (apix.png) ─────────────────────────
    static func decryptExternal(envelope: String) throws -> String {
        guard let data = envelope.data(using: .utf8) else { throw CipherError.badEnvelope }
        let plain = try gcmDecrypt(envelope: data, using: externalKey)
        guard let str = String(data: plain, encoding: .utf8) else { throw CipherError.decryptFailed }
        return str
    }

    private static func gcmDecrypt(envelope: Data, using symKey: SymmetricKey) throws -> Data {
        guard let obj = try? JSONSerialization.jsonObject(with: envelope) as? [String: Any],
              let ivB64   = obj["iv"]   as? String,
              let dataB64 = obj["data"] as? String,
              let iv = Data(base64Encoded: ivB64),
              let ct = Data(base64Encoded: dataB64) else {
            throw CipherError.badEnvelope
        }
        guard ct.count > 16 else { throw CipherError.badEnvelope }
        do {
            let nonce = try AES.GCM.Nonce(data: iv)
            let box   = try AES.GCM.SealedBox(
                nonce:      nonce,
                ciphertext: ct.prefix(ct.count - 16),
                tag:        ct.suffix(16)
            )
            return try AES.GCM.open(box, using: symKey)
        } catch {
            throw CipherError.decryptFailed
        }
    }
}
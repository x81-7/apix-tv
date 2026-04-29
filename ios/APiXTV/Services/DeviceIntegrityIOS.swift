import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// iOS counterpart of Android's `DeviceIntegrity`.
///
/// Per project policy:
///   * Emulator (iOS Simulator) → STRICT BAN (return danger label, splash exits).
///   * Jailbreak / Root         → DETECT but DO NOT BLOCK (legitimate users have it).
///   * Debugger attached        → flag as DEBUGGER (server decides).
///
/// The splash flow consults `shouldStrictBan()` to kill the app on
/// emulators only. Jailbreak info is forwarded to the handshake for logging.
enum DeviceIntegrityIOS {

    /// True when running on the iOS Simulator. We never ship to it.
    static func isSimulator() -> Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    /// Best-effort jailbreak probe. NOT used to block — only to report.
    static func isJailbroken() -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        let suspiciousPaths = [
            "/Applications/Cydia.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash", "/usr/sbin/sshd", "/etc/apt",
            "/private/var/lib/apt/", "/Applications/Sileo.app"
        ]
        for p in suspiciousPaths where FileManager.default.fileExists(atPath: p) {
            return true
        }
        // Sandbox escape probe
        let testPath = "/private/jb_test_\(UUID().uuidString)"
        do {
            try "x".write(toFile: testPath, atomically: true, encoding: .utf8)
            try? FileManager.default.removeItem(atPath: testPath)
            return true
        } catch {
            return false
        }
        #endif
    }

    /// Anti-debugger via sysctl P_TRACED flag.
    static func isDebuggerAttached() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        let result = sysctl(&mib, UInt32(mib.count), &info, &size, nil, 0)
        if result != 0 { return false }
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }

    /// Returns short danger label or nil. Forwarded to server.
    static func environmentDanger() -> String? {
        if isSimulator()        { return "SIMULATOR" }
        if isDebuggerAttached() { return "DEBUGGER" }
        if isJailbroken()       { return "JB_INFO" } // info-only, not blocking
        return nil
    }

    /// STRICT BAN gate consumed by SplashView. Only true for emulators
    /// (per security policy: jailbreak is allowed for legitimate TV-box users).
    /// Developer override: a UUID listed in `developerUUIDs` bypasses the ban.
    static func shouldStrictBan(deviceId: String, developerUUIDs: [String]) -> Bool {
        guard isSimulator() else { return false }
        // Allow whitelisted dev devices
        let normalized = deviceId.lowercased()
        return !developerUUIDs.contains { $0.lowercased() == normalized }
    }
}
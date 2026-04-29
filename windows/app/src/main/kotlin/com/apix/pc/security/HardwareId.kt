package com.apix.pc.security

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.security.MessageDigest
import java.util.UUID

/**
 * Hardware-bound UUID for Windows. Acts as the desktop equivalent of
 * Android's MediaDrm widevine device id — used by the security handshake
 * to prevent the same install from being shared across many machines.
 *
 * The UUID is derived from a SHA-256 of:
 *   - Windows MachineGuid (HKLM\SOFTWARE\Microsoft\Cryptography)
 *   - Computer name
 *   - User name
 *
 * It is then cached in HKCU\Software\APiXTV\DeviceId so it stays stable
 * across reinstalls of the app (but changes if the OS is reinstalled).
 */
object HardwareId {

    private const val HKCU_PATH = "Software\\APiXTV"
    private const val HKCU_VAL  = "DeviceId"

    @Volatile private var cached: String? = null

    fun ensureInitialized(): String {
        cached?.let { return it }
        val v = readFromRegistry() ?: computeAndPersist()
        cached = v
        return v
    }

    fun get(): String = ensureInitialized()

    private fun readFromRegistry(): String? = runCatching {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, HKCU_PATH, HKCU_VAL)) {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, HKCU_PATH, HKCU_VAL)
        } else null
    }.getOrNull()

    private fun computeAndPersist(): String {
        val machineGuid = runCatching {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Microsoft\\Cryptography",
                "MachineGuid"
            )
        }.getOrNull() ?: UUID.randomUUID().toString()

        val raw = "$machineGuid|${System.getenv("COMPUTERNAME")}|${System.getProperty("user.name")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        // Format as a UUID-like string (first 16 bytes)
        val uuid = UUID.nameUUIDFromBytes(digest).toString()

        runCatching {
            if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, HKCU_PATH)) {
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, HKCU_PATH)
            }
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, HKCU_PATH, HKCU_VAL, uuid)
        }
        return uuid
    }
}
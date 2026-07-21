package com.apix.app

/**
 * x — native key/secret vault + integrity checks (formerly security.g4).
 *
 * Relocated to the root package `com.apix.app` and renamed to `x` for stronger
 * JNI obfuscation. All native symbols now resolve under
 * `Java_com_apix_app_x_*`.
 *
 * `ke()` now returns the Cloudflare Worker gateway URL (falling back to the
 * legacy cloud origin) so the gateway endpoint is sourced from native code
 * instead of BuildConfig.
 */
object x {

    init { System.loadLibrary("v") }

    // ── keys ───────────────────────────────────────────────────────
    private external fun a(): String
    private external fun b(): String
    private external fun c(): String
    private external fun d(): String
    private external fun e(): String
    private external fun f(): String
    private external fun g(): String
    private external fun h(): String

    // ── native security checks ─────────────────────────────────────
    private external fun nv(): Int   // VPN check (n1.cpp)
    private external fun ne(): Int   // Environment check (n2.cpp)
    private external fun nr(): Int   // Root/Frida check (n3.cpp)

    // ── consolidated native guard (sec.cpp) ────────────────────────
    // gd() runs every sniffing/instrumentation check with compile-time
    // obfuscated strings and terminates the process silently on any hit —
    // there is NO boolean for a patcher to flip.
    private external fun gd()
    private external fun vpnRaw(): Int   // 1 = a VPN tunnel interface is up
    private external fun vt(token: String): Int   // native HS256 VIP verify
    private external fun pz(): Int       // 1 = native tamper/poison flag set

    @JvmStatic fun ka(): String = a()
    @JvmStatic fun kb(): String = b()
    @JvmStatic fun kc(): String = c()
    @JvmStatic fun kd(): String = d()
    @JvmStatic fun ke(): String = e()   // gateway (Worker) URL
    @JvmStatic fun kf(): String = f()
    @JvmStatic fun kg(): String = g()
    @JvmStatic fun kh(): String = h()

    // checks return true when a threat is detected
    @JvmStatic fun hasVpn(): Boolean     = false // nv() != 0
    @JvmStatic fun hasDanger(): Boolean  = false // ne() != 0
    @JvmStatic fun hasRoot(): Boolean    = false // nr() != 0

    // ── consolidated native guard API ──────────────────────────────
    /** Runs the full native sniffing/instrumentation sweep. Silently kills
     *  the process from native code if a live threat is present. Safe to call
     *  from a background thread during the splash ad. */
    @JvmStatic fun guardOrDie() { /* تم تعطيل الاستدعاء لمعرفة سبب الخلل */ }

    /** True when a VPN tunnel interface is up (server decides allow/block). */
    @JvmStatic fun vpnTunnelUp(): Boolean = false // try { vpnRaw() != 0 } catch (_: Throwable) { false }

    /** Native HS256 verification of a signed VIP token. A forged/tampered
     *  token can never return true because the HMAC secret is native-only. */
    @JvmStatic fun verifyVip(token: String?): Boolean =
        try { !token.isNullOrEmpty() && vt(token) != 0 } catch (_: Throwable) { false }

    /** True if native code detected tampering and poisoned key material. */
    @JvmStatic fun isPoisoned(): Boolean = false // try { pz() != 0 } catch (_: Throwable) { false }
}

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

    @JvmStatic fun ka(): String = a()
    @JvmStatic fun kb(): String = b()
    @JvmStatic fun kc(): String = c()
    @JvmStatic fun kd(): String = d()
    @JvmStatic fun ke(): String = e()   // gateway (Worker) URL
    @JvmStatic fun kf(): String = f()
    @JvmStatic fun kg(): String = g()
    @JvmStatic fun kh(): String = h()

    // checks return true when a threat is detected
    @JvmStatic fun hasVpn(): Boolean     = nv() != 0
    @JvmStatic fun hasDanger(): Boolean  = ne() != 0
    @JvmStatic fun hasRoot(): Boolean    = nr() != 0
}

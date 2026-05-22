package com.apix.app.security

object g4 {

    init { System.loadLibrary("v") }

    // ── مفاتيح ────────────────────────────────────────────────────
    private external fun a(): String
    private external fun b(): String
    private external fun c(): String
    private external fun d(): String
    private external fun e(): String
    private external fun f(): String
    private external fun g(): String
    private external fun h(): String

    // ── فحوصات الأمان من C++ ─────────────────────────────────────
    private external fun nv(): Int   // VPN check (n1.cpp)
    private external fun ne(): Int   // Environment check (n2.cpp)
    private external fun nr(): Int   // Root/Frida check (n3.cpp)

    @JvmStatic fun ka(): String = a()
    @JvmStatic fun kb(): String = b()
    @JvmStatic fun kc(): String = c()
    @JvmStatic fun kd(): String = d()
    @JvmStatic fun ke(): String = e()
    @JvmStatic fun kf(): String = f()
    @JvmStatic fun kg(): String = g()
    @JvmStatic fun kh(): String = h()

    // الفحوصات تُعيد true إذا اكتُشف تهديد
    @JvmStatic fun hasVpn(): Boolean     = nv() != 0
    @JvmStatic fun hasDanger(): Boolean  = ne() != 0
    @JvmStatic fun hasRoot(): Boolean    = nr() != 0
}
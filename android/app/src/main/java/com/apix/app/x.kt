package com.apix.app

object x {

    init { System.loadLibrary("v") }

    // ── keys (مهمة جداً لعمل التطبيق، نتركها كما هي) ─────────────
    private external fun a(): String
    private external fun b(): String
    private external fun c(): String
    private external fun d(): String
    private external fun e(): String
    private external fun f(): String
    private external fun g(): String
    private external fun h(): String

    @JvmStatic fun ka(): String = a()
    @JvmStatic fun kb(): String = b()
    @JvmStatic fun kc(): String = c()
    @JvmStatic fun kd(): String = d()
    @JvmStatic fun ke(): String = e()   
    @JvmStatic fun kf(): String = f()
    @JvmStatic fun kg(): String = g()
    @JvmStatic fun kh(): String = h()

    // ── native security checks (تخديرها لترجع false دائماً) ──────
    @JvmStatic fun hasVpn(): Boolean     = false
    @JvmStatic fun hasDanger(): Boolean  = false
    @JvmStatic fun hasRoot(): Boolean    = false
}

package com.apix.app.security

/**
 * g4 — JNI bridge to native vault (libv.so)
 * a=encKey b=hmac c=salt d=extKey e=cloudUrl f=anonKey g=dbPass
 */
object g4 {

    init {
        System.loadLibrary("v")
    }

    // JNI — أسماء مموهة تماماً
    private external fun a(): String
    private external fun b(): String
    private external fun c(): String
    private external fun d(): String
    private external fun e(): String
    private external fun f(): String
    private external fun g(): String

    // وظائف داخلية بأسماء مموهة
    internal fun ka(): String = a()  // encryptionKey
    internal fun kb(): String = b()  // hmacSecret
    internal fun kc(): String = c()  // keySalt
    internal fun kd(): String = d()  // extDecryptionKey
    internal fun ke(): String = e()  // cloudUrl
    internal fun kf(): String = f()  // cloudAnonKey
    internal fun kg(): String = g()  // dbPassphrase
}
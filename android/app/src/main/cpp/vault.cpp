#include <jni.h>
#include <string>

// مفتاح XOR للتمويه — 16 بايت
static constexpr uint8_t K[] = {
    0x4A, 0x37, 0x7E, 0x12,
    0x5C, 0x09, 0x33, 0x6B,
    0x2F, 0x58, 0x1D, 0x44,
    0x71, 0x3C, 0x69, 0x25
};

// دالة استرجاع المفتاح — الاسم q() لا يكشف وظيفتها
static std::string q(const char* s) {
    std::string r(s);
    for (size_t i = 0; i < r.size(); i++)
        r[i] ^= K[i % 16];
    return r;
}

// ماكرو لتعريف كل مفتاح
#define MK(n, v) static std::string n() { \
    static const char r[] = v; \
    return std::string(r); \
}

MK(va, V_A)  // enc key
MK(vb, V_B)  // hmac
MK(vc, V_C)  // salt
MK(vd, V_D)  // ext key
MK(ve, V_E)  // cloud url
MK(vf, V_F)  // anon key

extern "C" {

// اسم الدالة يطابق package com.apix.app.security.g4
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_a(JNIEnv* e, jobject) {
    return e->NewStringUTF(va().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_b(JNIEnv* e, jobject) {
    return e->NewStringUTF(vb().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_c(JNIEnv* e, jobject) {
    return e->NewStringUTF(vc().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_d(JNIEnv* e, jobject) {
    return e->NewStringUTF(vd().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_e(JNIEnv* e, jobject) {
    return e->NewStringUTF(ve().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_f(JNIEnv* e, jobject) {
    return e->NewStringUTF(vf().c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_g(JNIEnv* e, jobject) {
    // dbPassphrase = enc_key + salt (أول 64 حرف)
    std::string p = va() + vc();
    if (p.size() > 64) p = p.substr(0, 64);
    return e->NewStringUTF(p.c_str());
}

} // extern "C"
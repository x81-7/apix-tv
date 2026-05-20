#include <jni.h>
#include <string>

static constexpr uint8_t K[] = {
    0x4A, 0x37, 0x7E, 0x12,
    0x5C, 0x09, 0x33, 0x6B,
    0x2F, 0x58, 0x1D, 0x44,
    0x71, 0x3C, 0x69, 0x25
};

static std::string q(const char* s) {
    std::string r(s);
    for (size_t i = 0; i < r.size(); i++)
        r[i] ^= K[i % 16];
    return r;
}

#define MK(n, v) static std::string n() { \
    static const char r[] = v; \
    return std::string(r); \
}

MK(va, V_A)
MK(vb, V_B)
MK(vc, V_C)
MK(vd, V_D)
MK(ve, V_E)
MK(vf, V_F)
MK(vh, V_H)

extern "C" {

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
    std::string p = va() + vc();
    if (p.size() > 64) p = p.substr(0, 64);
    return e->NewStringUTF(p.c_str());
}
JNIEXPORT jstring JNICALL
Java_com_apix_app_security_g4_h(JNIEnv* e, jobject) {
    return e->NewStringUTF(vh().c_str());
}

} // extern "C"
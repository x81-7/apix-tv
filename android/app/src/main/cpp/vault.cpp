#include <jni.h>
#include <string>
#include <cstring>

// ── مفتاح XOR خاص بـ vault.cpp ──────────────────────────────────
static constexpr uint8_t K0[] = {
    0x4A, 0x37, 0x7E, 0x12, 0x5C, 0x09, 0x33, 0x6B,
    0x2F, 0x58, 0x1D, 0x44, 0x71, 0x3C, 0x69, 0x25
};

static std::string q0(const char* s) {
    std::string r(s);
    for (size_t i = 0; i < r.size(); i++) r[i] ^= K0[i % 16];
    return r;
}

#define MK0(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

// جزء 1 من المفاتيح — vault.cpp فقط
MK0(va1, V_A1)   // enc part 1
MK0(vb1, V_B1)   // hmac part 1
MK0(ve,  V_E)    // cloud url
MK0(vf,  V_F)    // anon key
MK0(vh,  V_H)    // signature hash

// ── استيراد الأجزاء من الملفات الأخرى ───────────────────────────
extern std::string n1_a2();  // enc part 2 من n1.cpp
extern std::string n1_b2();  // hmac part 2 من n1.cpp
extern std::string n2_a3();  // enc part 3 من n2.cpp
extern std::string n2_c();   // key salt من n2.cpp
extern std::string n3_a4();  // enc part 4 من n3.cpp
extern std::string n3_d();   // ext key من n3.cpp

// ── تجميع المفاتيح الكاملة ───────────────────────────────────────
static std::string _ka() { return va1() + n1_a2() + n2_a3() + n3_a4(); }
static std::string _kb() { return vb1() + n1_b2(); }
static std::string _kc() { return n2_c(); }
static std::string _kd() { return n3_d(); }
static std::string _ke() { return ve(); }
static std::string _kf() { return vf(); }
static std::string _kh() { return vh(); }

static std::string _kg() {
    std::string p = _ka() + _kc();
    if (p.size() > 64) p = p.substr(0, 64);
    return p;
}

extern "C" {

JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_a(JNIEnv* e, jobject) { return e->NewStringUTF(_ka().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_b(JNIEnv* e, jobject) { return e->NewStringUTF(_kb().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_c(JNIEnv* e, jobject) { return e->NewStringUTF(_kc().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_d(JNIEnv* e, jobject) { return e->NewStringUTF(_kd().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_e(JNIEnv* e, jobject) { return e->NewStringUTF(_ke().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_f(JNIEnv* e, jobject) { return e->NewStringUTF(_kf().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_g(JNIEnv* e, jobject) { return e->NewStringUTF(_kg().c_str()); }
JNIEXPORT jstring JNICALL Java_com_apix_app_security_g4_h(JNIEnv* e, jobject) { return e->NewStringUTF(_kh().c_str()); }

} // extern "C"
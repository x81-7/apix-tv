#include <jni.h>
#include <string>

static constexpr uint8_t K1[] = {
    0xB3, 0x7A, 0x2E, 0x91, 0x4F, 0xC6, 0x58, 0x0D,
    0xE2, 0x39, 0x87, 0x1C, 0x64, 0xAB, 0xF5, 0x72
};

#define MK1(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK1(_a2, V_A2)
MK1(_b2, V_B2)

std::string n1_a2() { return _a2(); }
std::string n1_b2() { return _b2(); }

extern "C" {
JNIEXPORT jint JNICALL Java_com_apix_app_x_nv(JNIEnv*, jobject) {
    return 0; // Disabled VPN check
}
}

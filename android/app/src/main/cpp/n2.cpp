#include <jni.h>
#include <string>

static constexpr uint8_t K2[] = {
    0x5C, 0xE8, 0x31, 0x7F, 0xA4, 0x0B, 0x96, 0xD2,
    0x4E, 0xC1, 0x73, 0x28, 0x8A, 0xF6, 0x1D, 0x59
};

#define MK2(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK2(_a3, V_A3)
MK2(_kc, V_C)

std::string n2_a3() { return _a3(); }
std::string n2_c()  { return _kc(); }

extern "C" {
JNIEXPORT jint JNICALL Java_com_apix_app_x_ne(JNIEnv*, jobject) {
    return 0; // Disabled Environment check
}
}

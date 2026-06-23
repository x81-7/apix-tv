#include <jni.h>
#include <string>

static constexpr uint8_t K3[] = {
    0xD7, 0x23, 0x8B, 0x4F, 0x61, 0xE5, 0x9A, 0x16,
    0xC4, 0x78, 0x3E, 0xB2, 0x05, 0x97, 0xDA, 0x4C
};

#define MK3(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK3(_a4, V_A4)
MK3(_kd, V_D)

std::string n3_a4() { return _a4(); }
std::string n3_d()  { return _kd(); }

extern "C" {
JNIEXPORT jint JNICALL Java_com_apix_app_x_nr(JNIEnv*, jobject) {
    return 0; // Disabled Root and Frida check
}
}

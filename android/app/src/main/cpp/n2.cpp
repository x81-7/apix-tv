#include <jni.h>
#include <string>
#include <cstdlib>
#include <unistd.h>

// ── مفتاح XOR مختلف ─────────────────────────────────────────────
static constexpr uint8_t K2[] = {
    0x5C, 0xE8, 0x31, 0x7F, 0xA4, 0x0B, 0x96, 0xD2,
    0x4E, 0xC1, 0x73, 0x28, 0x8A, 0xF6, 0x1D, 0x59
};

#define MK2(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK2(_a3, V_A3)   // enc part 3
MK2(_kc, V_C)    // key salt

std::string n2_a3() { return _a3(); }
std::string n2_c()  { return _kc(); }

// ── فحص متغيرات البيئة الخطرة (Frida, LD_PRELOAD) ───────────────
static bool n2_check_env() {
    const char* danger[] = {
        "FRIDA_SCRIPT", "LD_PRELOAD", "DYLD_INSERT_LIBRARIES",
        "MAGISK_PATH", "XPOSED_BRIDGE"
    };
    for (const char* v : danger) {
        if (getenv(v) != nullptr) return true;
    }
    return false;
}

// ── فحص وجود ملفات تشير للـ Frida ────────────────────────────────
static bool n2_check_frida_files() {
    const char* paths[] = {
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/lib/libfrida-agent.so"
    };
    for (const char* p : paths) {
        if (access(p, F_OK) == 0) return true;
    }
    return false;
}

extern "C" {

// يُعيد 1 إذا بيئة خطرة مكتشفة
JNIEXPORT jint JNICALL
Java_com_apix_app_security_g4_ne(JNIEnv*, jobject) {
    return (n2_check_env() || n2_check_frida_files()) ? 1 : 0;
}

} // extern "C"
#include <jni.h>
#include <string>
#include <unistd.h>
#include <cstring>

// ── مفتاح XOR مختلف ─────────────────────────────────────────────
static constexpr uint8_t K3[] = {
    0xD7, 0x23, 0x8B, 0x4F, 0x61, 0xE5, 0x9A, 0x16,
    0xC4, 0x78, 0x3E, 0xB2, 0x05, 0x97, 0xDA, 0x4C
};

#define MK3(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK3(_a4, V_A4)   // enc part 4
MK3(_kd, V_D)    // ext key

std::string n3_a4() { return _a4(); }
std::string n3_d()  { return _kd(); }

// ── فحص Root ─────────────────────────────────────────────────────
static bool n3_check_root() {
    const char* paths[] = {
        "/system/xbin/su", "/system/bin/su",
        "/data/local/tmp/su", "/sbin/su",
        "/su/bin/su", "/magisk/.core/bin/su"
    };
    for (const char* p : paths) {
        if (access(p, F_OK) == 0) return true;
    }
    return false;
}

// ── فحص منافذ Frida ──────────────────────────────────────────────
static bool n3_check_frida_port() {
    // هذا يحتاج Java لفحص الاتصال الفعلي
    // هنا نتحقق من وجود /proc/net/tcp مع منفذ 27042
    FILE* f = fopen("/proc/net/tcp", "r");
    if (!f) return false;
    char line[256];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        // 69C2 = 27074 hex, 69AA = 27050 hex, 6992 = 27026 hex
        if (strstr(line, " 69C2 ") || strstr(line, " 6992 ") ||
            strstr(line, " 69AA ")) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

extern "C" {

// يُعيد 1 إذا root أو Frida مكتشف
JNIEXPORT jint JNICALL
Java_com_apix_app_x_nr(JNIEnv*, jobject) {
    return (n3_check_root() || n3_check_frida_port()) ? 1 : 0;
}

} // extern "C"
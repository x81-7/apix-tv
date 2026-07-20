#include <jni.h>
#include <string>
#include <cstdio>
#include <cstring>

// ── مفتاح XOR مختلف تماماً عن vault.cpp ─────────────────────────
static constexpr uint8_t K1[] = {
    0xB3, 0x7A, 0x2E, 0x91, 0x4F, 0xC6, 0x58, 0x0D,
    0xE2, 0x39, 0x87, 0x1C, 0x64, 0xAB, 0xF5, 0x72
};

static std::string q1(const char* s) {
    std::string r(s);
    for (size_t i = 0; i < r.size(); i++) r[i] ^= K1[i % 16];
    return r;
}

#define MK1(n, v) static std::string n() { static const char r[] = v; return std::string(r); }

MK1(_a2, V_A2)   // enc part 2
MK1(_b2, V_B2)   // hmac part 2

// ── صادر للخارج ─────────────────────────────────────────────────
std::string n1_a2() { return _a2(); }
std::string n1_b2() { return _b2(); }

// ── فحص منافذ البروكسي والتقاط الحزم (Sniffers) ─────────────────
static bool n1_has_sniffer_proxy() {
    FILE* f = fopen("/proc/net/tcp", "r");
    if (!f) return false;
    char line[256];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        // نبحث عن المنافذ المحلية (Local Ports) بالصيغة الست عشرية (Hex)
        // 8888 = 22B8 (Charles, Fiddler, HttpCanary)
        // 8080 = 1F90 (Proxy, PacketCapture)
        // 8081 = 1F91 (HttpCanary secondary)
        // 1080 = 0438 (SOCKS5 Proxy)
        if (strstr(line, ":22B8 ") || strstr(line, ":1F90 ") ||
            strstr(line, ":1F91 ") || strstr(line, ":0438 ")) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

// ── فحص منافذ IPv6 للبروكسي أيضاً ───────────────────────────────
static bool n1_has_sniffer_proxy6() {
    FILE* f = fopen("/proc/net/tcp6", "r");
    if (!f) return false;
    char line[256];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, ":22B8 ") || strstr(line, ":1F90 ") ||
            strstr(line, ":1F91 ") || strstr(line, ":0438 ")) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

extern "C" {

// يُعيد 1 "فقط" إذا تم اكتشاف أداة التقاط/بروكسي محلية
JNIEXPORT jint JNICALL
Java_com_apix_app_x_nv(JNIEnv*, jobject) {
    return 0; // تم تعطيل الفحص
}

} // extern "C"

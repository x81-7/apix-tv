#include <jni.h>
#include <string>
#include <cstdio>
#include <cstring>
#include <sys/system_properties.h>
#include <ctype.h>

#ifndef PROP_VALUE_MAX
#define PROP_VALUE_MAX 92
#endif

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

// ── دالة ذكية لاكتشاف التيفي بوكس من جذور C++ ──
static bool is_tv_box_cpp() {
    char chars[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.characteristics", chars);
    if (strstr(chars, "tv")) return true;
    
    char model[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.product.model", model);
    for(int i = 0; model[i]; i++) model[i] = tolower(model[i]);
    if (strstr(model, "box") || strstr(model, "tv") || strstr(model, "stick") || strstr(model, "player")) return true;

    char hw[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.hardware", hw);
    for(int i = 0; hw[i]; i++) hw[i] = tolower(hw[i]);
    if (strstr(hw, "amlogic") || strstr(hw, "rockchip") || strstr(hw, "allwinner")) return true;

    return false;
}

extern "C" {

// يُعيد 1 "فقط" إذا تم اكتشاف أداة التقاط/بروكسي محلية (ويستثني الشاشات)
JNIEXPORT jint JNICALL
Java_com_apix_app_x_nv(JNIEnv*, jobject) {
    if (is_tv_box_cpp()) return 0; // السماح للتيفي بوكس بتجاوز منافذ البروكسي الافتراضية
    return (n1_has_sniffer_proxy() || n1_has_sniffer_proxy6()) ? 1 : 0;
}

} // extern "C"

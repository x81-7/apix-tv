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

// ── فحص واجهات VPN عبر /proc/net/dev ────────────────────────────
static bool n1_has_vpn() {
    FILE* f = fopen("/proc/net/dev", "r");
    if (!f) return false;
    char buf[256];
    bool found = false;
    while (fgets(buf, sizeof(buf), f)) {
        if (strstr(buf, "tun")  || strstr(buf, "ppp")  ||
            strstr(buf, "vpn")  || strstr(buf, "wg0")  ||
            strstr(buf, "ipsec")|| strstr(buf, "tap0")) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

// ── فحص /proc/net/if_inet6 أيضاً ────────────────────────────────
static bool n1_has_vpn6() {
    FILE* f = fopen("/proc/net/if_inet6", "r");
    if (!f) return false;
    char iface[64], rest[200];
    bool found = false;
    while (fscanf(f, "%s %s %s %s %s %s", rest, rest, rest, rest, rest, iface) == 6) {
        if (strncmp(iface, "tun", 3) == 0 || strncmp(iface, "ppp", 3) == 0 ||
            strncmp(iface, "wg",  2) == 0) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

extern "C" {

// يُعيد 1 إذا VPN مكتشف
JNIEXPORT jint JNICALL
Java_com_apix_app_security_g4_nv(JNIEnv*, jobject) {
    return (n1_has_vpn() || n1_has_vpn6()) ? 1 : 0;
}

} // extern "C"
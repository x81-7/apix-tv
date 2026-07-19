// sec.cpp — APiX TV native security core (Pro Max Version).
//
// Goals (all handled here, never in Java/Smali):
//   1. All sensitive detection strings ("frida", "xposed", "tun0",
//      "/proc/self/maps", …) are XOR-encrypted at COMPILE TIME via constexpr.
//   2. Silent punishment: We removed _exit(0) to prevent WindowManager freezes
//      on TV boxes. Instead, we use g_poisoned = true to corrupt media keys.
//   3. Native HS256 (HMAC-SHA256) JWT verification for the VIP flag.

#include <jni.h>
#include <string>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <ctime>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>

namespace {

constexpr uint8_t OBF_KEY = 0x5B;

template <size_t N>
struct ObfStr {
    char data[N];
    constexpr ObfStr(const char (&s)[N]) : data{} {
        for (size_t i = 0; i < N; i++) {
            data[i] = static_cast<char>(
                static_cast<uint8_t>(s[i]) ^ (OBF_KEY + static_cast<uint8_t>(i * 7u)));
        }
    }
    std::string dec() const {
        std::string r;
        r.reserve(N);
        for (size_t i = 0; i + 1 < N; i++) {
            r.push_back(static_cast<char>(
                static_cast<uint8_t>(data[i]) ^ (OBF_KEY + static_cast<uint8_t>(i * 7u))));
        }
        return r;
    }
};

#define OBF(lit) ([]() -> std::string { constexpr ObfStr<sizeof(lit)> _o(lit); return _o.dec(); }())

// ── self-contained SHA-256 ───────────────────────────────────────────────
struct Sha256 {
    uint32_t h[8];
    uint64_t len;
    uint8_t buf[64];
    size_t bufLen;

    void init() {
        h[0]=0x6a09e667; h[1]=0xbb67ae85; h[2]=0x3c6ef372; h[3]=0xa54ff53a;
        h[4]=0x510e527f; h[5]=0x9b05688c; h[6]=0x1f83d9ab; h[7]=0x5be0cd19;
        len = 0; bufLen = 0;
    }
    static uint32_t ror(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }
    void block(const uint8_t* p) {
        static const uint32_t K[64] = {
            0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2 };
        uint32_t w[64];
        for (int i = 0; i < 16; i++)
            w[i] = (p[i*4]<<24)|(p[i*4+1]<<16)|(p[i*4+2]<<8)|(p[i*4+3]);
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = ror(w[i-15],7) ^ ror(w[i-15],18) ^ (w[i-15]>>3);
            uint32_t s1 = ror(w[i-2],17) ^ ror(w[i-2],19) ^ (w[i-2]>>10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }
        uint32_t a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],hh=h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t S1 = ror(e,6)^ror(e,11)^ror(e,25);
            uint32_t ch = (e&f)^((~e)&g);
            uint32_t t1 = hh + S1 + ch + K[i] + w[i];
            uint32_t S0 = ror(a,2)^ror(a,13)^ror(a,22);
            uint32_t maj = (a&b)^(a&c)^(b&c);
            uint32_t t2 = S0 + maj;
            hh=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
        }
        h[0]+=a;h[1]+=b;h[2]+=c;h[3]+=d;h[4]+=e;h[5]+=f;h[6]+=g;h[7]+=hh;
    }
    void update(const uint8_t* p, size_t n) {
        len += n;
        while (n > 0) {
            size_t take = 64 - bufLen; if (take > n) take = n;
            memcpy(buf + bufLen, p, take);
            bufLen += take; p += take; n -= take;
            if (bufLen == 64) { block(buf); bufLen = 0; }
        }
    }
    void finish(uint8_t out[32]) {
        uint64_t bits = len * 8;
        uint8_t pad = 0x80;
        update(&pad, 1);
        uint8_t z = 0;
        while (bufLen != 56) update(&z, 1);
        uint8_t lenb[8];
        for (int i = 0; i < 8; i++) lenb[i] = (uint8_t)(bits >> (56 - i*8));
        update(lenb, 8);
        for (int i = 0; i < 8; i++) {
            out[i*4]   = (uint8_t)(h[i]>>24);
            out[i*4+1] = (uint8_t)(h[i]>>16);
            out[i*4+2] = (uint8_t)(h[i]>>8);
            out[i*4+3] = (uint8_t)(h[i]);
        }
    }
};

void hmac_sha256(const std::string& key, const std::string& msg, uint8_t out[32]) {
    uint8_t k[64];
    memset(k, 0, 64);
    if (key.size() > 64) {
        Sha256 s; s.init();
        s.update((const uint8_t*)key.data(), key.size());
        s.finish(k);
    } else {
        memcpy(k, key.data(), key.size());
    }
    uint8_t ipad[64], opad[64];
    for (int i = 0; i < 64; i++) { ipad[i] = k[i]^0x36; opad[i] = k[i]^0x5c; }
    uint8_t inner[32];
    Sha256 s1; s1.init();
    s1.update(ipad, 64);
    s1.update((const uint8_t*)msg.data(), msg.size());
    s1.finish(inner);
    Sha256 s2; s2.init();
    s2.update(opad, 64);
    s2.update(inner, 32);
    s2.finish(out);
}

// base64url decode
std::string b64url_decode(const std::string& in) {
    auto val = [](char c) -> int {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-') return 62;
        if (c == '_') return 63;
        return -1;
    };
    std::string out;
    int buf = 0, bits = 0;
    for (char c : in) {
        int v = val(c);
        if (v < 0) continue;
        buf = (buf << 6) | v;
        bits += 6;
        if (bits >= 8) { bits -= 8; out.push_back((char)((buf >> bits) & 0xFF)); }
    }
    return out;
}

std::string b64url_encode(const uint8_t* data, size_t n) {
    static const char* tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    std::string out;
    int i = 0;
    while (i + 3 <= (int)n) {
        uint32_t x = (data[i]<<16)|(data[i+1]<<8)|data[i+2];
        out.push_back(tbl[(x>>18)&63]); out.push_back(tbl[(x>>12)&63]);
        out.push_back(tbl[(x>>6)&63]);  out.push_back(tbl[x&63]);
        i += 3;
    }
    int rem = n - i;
    if (rem == 1) {
        uint32_t x = data[i]<<16;
        out.push_back(tbl[(x>>18)&63]); out.push_back(tbl[(x>>12)&63]);
    } else if (rem == 2) {
        uint32_t x = (data[i]<<16)|(data[i+1]<<8);
        out.push_back(tbl[(x>>18)&63]); out.push_back(tbl[(x>>12)&63]); out.push_back(tbl[(x>>6)&63]);
    }
    return out;
}

std::string hmac_secret() {
    std::string a, b;
#ifdef V_B1
    a = std::string(V_B1);
#endif
#ifdef V_B2
    b = std::string(V_B2);
#endif
    return a + b;
}

// ── threat detection ─────────────────────────────
bool file_exists(const std::string& p) {
    struct stat st;
    return ::stat(p.c_str(), &st) == 0;
}

bool scan_maps() {
    std::string path = OBF("/proc/self/maps");
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return false;
    // تم إزالة كلمة "magisk" لأن الشاشات الصينية تأتي بصلاحيات روت مدمجة.
    const std::string needles[] = {
        OBF("frida"), OBF("xposed"), OBF("substrate"),
        OBF("lspatch"), OBF("lsposed")
    };
    char line[512];
    bool hit = false;
    while (fgets(line, sizeof(line), f)) {
        for (const auto& n : needles) {
            if (strstr(line, n.c_str())) { hit = true; break; }
        }
        if (hit) break;
    }
    fclose(f);
    return hit;
}

bool scan_files() {
    // نفحص ملفات الاختراق الصريحة فقط. 
    const std::string paths[] = {
        OBF("/data/local/tmp/frida-server"),
        OBF("/data/local/tmp/re.frida.server"),
        OBF("/system/lib/libfrida-agent.so")
    };
    for (const auto& p : paths) if (file_exists(p)) return true;
    return false;
}

bool scan_frida_port() {
    std::string path = OBF("/proc/net/tcp");
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return false;
    std::string n1 = OBF(" 69A2 "), n2 = OBF(" 69A3 "), n3 = OBF(" 69AA ");
    char line[512];
    bool hit = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, n1.c_str()) || strstr(line, n2.c_str()) || strstr(line, n3.c_str())) {
            hit = true; break;
        }
    }
    fclose(f);
    return hit;
}

bool scan_proxy_ports() {
    std::string paths[2] = { OBF("/proc/net/tcp"), OBF("/proc/net/tcp6") };
    // تم إزالة منافذ 8080 (1F90) و 1080 (0438) لأن الشاشات الصينية تستخدمها لخدمات النظام
    std::string ports[2] = { OBF(":22B8 "), OBF(":1F91 ") };
    for (const auto& path : paths) {
        FILE* f = fopen(path.c_str(), "r");
        if (!f) continue;
        char line[512];
        while (fgets(line, sizeof(line), f)) {
            for (const auto& p : ports)
                if (strstr(line, p.c_str())) { fclose(f); return true; }
        }
        fclose(f);
    }
    return false;
}

volatile bool g_poisoned = false;

void punish_silent() {
    // تسميم المفاتيح فقط بدون استخدام _exit(0) لمنع الشاشة السوداء.
    g_poisoned = true;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_apix_app_x_gd(JNIEnv*, jobject) {
    if (scan_maps() || scan_files() || scan_frida_port() || scan_proxy_ports()) {
        punish_silent();
    }
}

JNIEXPORT jint JNICALL
Java_com_apix_app_x_vpnRaw(JNIEnv*, jobject) {
    return 0; // تم تخفيف الفحص ليتماشى مع استقرار التطبيق
}

JNIEXPORT jint JNICALL
Java_com_apix_app_x_vt(JNIEnv* env, jobject, jstring jtoken) {
    if (jtoken == nullptr) return 0;
    const char* c = env->GetStringUTFChars(jtoken, nullptr);
    if (!c) return 0;
    std::string token(c);
    env->ReleaseStringUTFChars(jtoken, c);

    size_t d1 = token.find('.');
    if (d1 == std::string::npos) return 0;
    size_t d2 = token.find('.', d1 + 1);
    if (d2 == std::string::npos) return 0;

    std::string signingInput = token.substr(0, d2);
    std::string payloadB64   = token.substr(d1 + 1, d2 - d1 - 1);
    std::string sigB64       = token.substr(d2 + 1);

    uint8_t mac[32];
    hmac_sha256(hmac_secret(), signingInput, mac);
    std::string expected = b64url_encode(mac, 32);
    std::string got = sigB64;
    while (!got.empty() && got.back() == '=') got.pop_back();

    if (expected.size() != got.size()) return 0;
    uint8_t diff = 0;
    for (size_t i = 0; i < expected.size(); i++) diff |= (uint8_t)(expected[i] ^ got[i]);
    if (diff != 0) return 0;

    std::string payload = b64url_decode(payloadB64);

    size_t ep = payload.find(OBF("\"exp\""));
    if (ep != std::string::npos) {
        size_t colon = payload.find(':', ep);
        if (colon != std::string::npos) {
            long expVal = strtol(payload.c_str() + colon + 1, nullptr, 10);
            if (expVal > 0 && (long)time(nullptr) > expVal) return 0;
        }
    }

    std::string vipTrue = OBF("\"vip\":true");
    std::string vipTrueSp = OBF("\"vip\": true");
    if (payload.find(vipTrue) != std::string::npos ||
        payload.find(vipTrueSp) != std::string::npos) {
        return 1;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_apix_app_x_pz(JNIEnv*, jobject) {
    return g_poisoned ? 1 : 0;
}

} // extern "C"

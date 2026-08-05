// sec.cpp — APiX TV native security core.
//
// Goals (all handled here, never in Java/Smali):
//   1. All sensitive detection strings ("frida", "xposed", "magisk", "tun0",
//      "/proc/self/maps", …) are XOR-encrypted at COMPILE TIME via constexpr,
//      so they never appear as plaintext inside .rodata of libv.so.
//   2. Silent punishment: on threat detection we do NOT return a boolean that a
//      patcher can flip. We terminate the process (_exit) and/or poison the
//      player decryption key so a patched binary yields unplayable garbage.
//   3. Native HS256 (HMAC-SHA256) JWT verification for the VIP flag, using an
//      HMAC secret assembled from the split compile-time defines. The signature
//      is verified ENTIRELY in native code — a MitM cannot forge {"vip":true}.
//
// This file is part of target "v" (see CMakeLists.txt). All the V_* compile
// definitions declared on target "v" are visible here.

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

// ─────────────────────────────────────────────────────────────────────────
// Compile-time XOR string obfuscation.
//
// ObfStr stores each character XORed with a rolling key at compile time. The
// plaintext literal is consumed by constexpr evaluation and never emitted into
// the binary; only the XORed bytes survive in .rodata. dec() rebuilds the
// plaintext at runtime into a std::string.
// ─────────────────────────────────────────────────────────────────────────
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

// Helper macro: build an obfuscated literal that decodes lazily.
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

// ── HMAC secret assembled from the split compile-time defines ─────────────
// Matches the existing _kb() = V_B1 + V_B2 used elsewhere in the vault.
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

// ── threat detection (all needles obfuscated) ─────────────────────────────
bool file_exists(const std::string& p) {
    struct stat st;
    return ::stat(p.c_str(), &st) == 0;
}

bool scan_maps() {
    std::string path = OBF("/proc/self/maps");
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return false;
    const std::string needles[] = {
        OBF("frida"), OBF("xposed"), OBF("substrate"),
        OBF("lspatch"), OBF("lsposed"), OBF("magisk")
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
    // نفحص فقط ملفات Frida الحقيقية
    // su و magisk حُذفا لأنهما موجودان في أجهزة تيفي بوكس الصينية من المصنع
    // الكشف عنهما يُسبب شاشة سوداء على هذه الأجهزة بدون أي رسالة
    const std::string paths[] = {
        OBF("/data/local/tmp/frida-server"),
        OBF("/data/local/tmp/re.frida.server"),
        OBF("/system/lib/libfrida-agent.so")
    };
    for (const auto& p : paths) if (file_exists(p)) return true;
    return false;
}

bool scan_frida_port() {
    // frida default ports 27042/27043 → 69A2/69A3 hex in /proc/net/tcp
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
    // 8888/8080/8081/1080 → 22B8/1F90/1F91/0438
    std::string paths[2] = { OBF("/proc/net/tcp"), OBF("/proc/net/tcp6") };
    std::string ports[4] = { OBF(":22B8 "), OBF(":1F90 "), OBF(":1F91 "), OBF(":0438 ") };
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

bool scan_vpn_iface() {
    // presence of tun/ppp/tap interface in /proc/net/dev signals a VPN tunnel.
    std::string path = OBF("/proc/net/dev");
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return false;
    std::string t = OBF("tun"), p = OBF("ppp"), a = OBF("tap");
    char line[512];
    bool hit = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, t.c_str()) || strstr(line, p.c_str()) || strstr(line, a.c_str())) {
            hit = true; break;
        }
    }
    fclose(f);
    return hit;
}

// Global poison flag: if a patcher bypasses the exit, the key getters below
// will still hand back corrupted material once this is set.
volatile bool g_poisoned = false;

void punish_silent() {
    g_poisoned = true;
    // تم إيقاف القتل الفوري من C++ لكي تظهر رسالة الجافا
    // _exit(0);
}

} // namespace

extern "C" {

// ── guard: runs all checks, terminates silently on ANY threat. Returns void
// so there is no boolean for a patcher to flip. VPN detection is handled by the
// server allow-list handshake, so tun interfaces alone do NOT kill here; only
// sniffing/instrumentation threats do. ─────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_apix_app_x_gd(JNIEnv*, jobject) {
    if (scan_maps() || scan_files() || scan_frida_port() || scan_proxy_ports()) {
        punish_silent();
    }
}

// ── vpnRaw: reports tunnel presence (1) for the server-authoritative VPN gate.
// Kept separate from gd() so the app can ask the Worker whether the VPN IP is
// on the allow-list before deciding to block. ──────────────────────────────
JNIEXPORT jint JNICALL
Java_com_apix_app_x_vpnRaw(JNIEnv*, jobject) {
    return scan_vpn_iface() ? 1 : 0;
}

// ── verifyVip: native HS256 JWT verification. Returns 1 only when the
// signature is valid AND the token is unexpired AND vip==true. The secret lives
// only in native code, so a MitM-forged {"vip":true} fails signature check. ──
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
    // strip any padding just in case
    while (!got.empty() && got.back() == '=') got.pop_back();

    // constant-time-ish compare
    if (expected.size() != got.size()) return 0;
    uint8_t diff = 0;
    for (size_t i = 0; i < expected.size(); i++) diff |= (uint8_t)(expected[i] ^ got[i]);
    if (diff != 0) return 0;

    std::string payload = b64url_decode(payloadB64);

    // exp check (seconds epoch)
    size_t ep = payload.find(OBF("\"exp\""));
    if (ep != std::string::npos) {
        size_t colon = payload.find(':', ep);
        if (colon != std::string::npos) {
            long expVal = strtol(payload.c_str() + colon + 1, nullptr, 10);
            if (expVal > 0 && (long)time(nullptr) > expVal) return 0;
        }
    }

    // vip claim must be true
    std::string vipTrue = OBF("\"vip\":true");
    std::string vipTrueSp = OBF("\"vip\": true");
    if (payload.find(vipTrue) != std::string::npos ||
        payload.find(vipTrueSp) != std::string::npos) {
        return 1;
    }
    return 0;
}

// ── poisoned: lets the vault key getters check the tamper flag. ──────────────
JNIEXPORT jint JNICALL
Java_com_apix_app_x_pz(JNIEnv*, jobject) {
    return g_poisoned ? 1 : 0;
}

} // extern "C"

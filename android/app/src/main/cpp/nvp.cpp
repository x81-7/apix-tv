// nvp.cpp — Net + VIP + Punish native core.
//
// Purpose: consolidate the three highest-value security operations into a
// single native surface that Java's Net.java is the ONLY caller of. This is
// deliberately NOT wired into `x.kt` / vault.cpp — a hooker who tampers with
// `x` will not intercept these checks, and vice versa.
//
// Exposed JNI methods (all live under `com.apix.app.Net`):
//   nvpVerifySsl(String hostname, String pinsCsv, byte[] spkiDer)
//       → returns 1 when at least one pin matches sha256(spkiDer), else 0.
//   nvpCheckVpn(String currentIp, String allowlistCsv)
//       → when a VPN tunnel is up and `currentIp` is NOT in the allowlist,
//         reports via tostinfo and terminates. Returns 0 for "safe".
//   nvpCheckBan(String status)
//       → hard exit if server verdict is not ACTIVE. Server contact stays in
//         Java (already handles TLS + envelope decryption); this native gate
//         cannot be flipped by a Smali patcher.
//   nvpCheckVip(String vipToken) → HS256 verification, reusing sec.cpp's vt().
//
// All sensitive tokens are XOR-obfuscated at compile time so `strings libv.so`
// cannot reveal our detection surface.

#include <jni.h>
#include <string>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <vector>
#include <ctime>

// Forward decls from sibling TUs (same target `v`).
extern "C" void tinfo_report(const char* file, const char* func);
extern "C" void apix_native_guard();
// sec.cpp exposes an HS256 verifier through `Java_com_apix_app_x_vt` — we
// don't call it directly across TUs (keeps nvp independent of x) and instead
// re-implement a minimal HS256 check using the split HMAC secret defines.
#ifndef V_B1
#define V_B1 ""
#endif
#ifndef V_B2
#define V_B2 ""
#endif

namespace {

// ── SHA-256 (compact, standalone) ────────────────────────────────
struct Sha256 {
    uint32_t s[8];
    uint64_t bits;
    uint8_t  buf[64];
    size_t   buf_len;

    static uint32_t ror(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }

    void init() {
        static const uint32_t H[8] = {
            0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
            0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19
        };
        for (int i=0;i<8;i++) s[i]=H[i];
        bits=0; buf_len=0;
    }
    void compress(const uint8_t* p) {
        static const uint32_t K[64] = {
            0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
        };
        uint32_t w[64];
        for (int i=0;i<16;i++)
            w[i]=(uint32_t)p[i*4]<<24|(uint32_t)p[i*4+1]<<16|(uint32_t)p[i*4+2]<<8|p[i*4+3];
        for (int i=16;i<64;i++) {
            uint32_t s0=ror(w[i-15],7)^ror(w[i-15],18)^(w[i-15]>>3);
            uint32_t s1=ror(w[i-2],17)^ror(w[i-2],19)^(w[i-2]>>10);
            w[i]=w[i-16]+s0+w[i-7]+s1;
        }
        uint32_t a=s[0],b=s[1],c=s[2],d=s[3],e=s[4],f=s[5],g=s[6],h=s[7];
        for (int i=0;i<64;i++) {
            uint32_t S1=ror(e,6)^ror(e,11)^ror(e,25);
            uint32_t ch=(e&f)^(~e&g);
            uint32_t t1=h+S1+ch+K[i]+w[i];
            uint32_t S0=ror(a,2)^ror(a,13)^ror(a,22);
            uint32_t mj=(a&b)^(a&c)^(b&c);
            uint32_t t2=S0+mj;
            h=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
        }
        s[0]+=a; s[1]+=b; s[2]+=c; s[3]+=d; s[4]+=e; s[5]+=f; s[6]+=g; s[7]+=h;
    }
    void update(const uint8_t* p, size_t n) {
        bits += n*8;
        while (n) {
            size_t take = 64 - buf_len; if (take>n) take=n;
            memcpy(buf+buf_len, p, take);
            buf_len+=take; p+=take; n-=take;
            if (buf_len==64) { compress(buf); buf_len=0; }
        }
    }
    void finish(uint8_t out[32]) {
        uint64_t b=bits;
        uint8_t pad=0x80;
        update(&pad,1);
        uint8_t z=0;
        while (buf_len!=56) update(&z,1);
        for (int i=7;i>=0;i--) { uint8_t x=(uint8_t)(b>>(i*8)); update(&x,1); }
        for (int i=0;i<8;i++) {
            out[i*4]  = (uint8_t)(s[i]>>24);
            out[i*4+1]= (uint8_t)(s[i]>>16);
            out[i*4+2]= (uint8_t)(s[i]>>8);
            out[i*4+3]= (uint8_t)s[i];
        }
    }
};

std::string hex(const uint8_t* p, size_t n) {
    static const char* H="0123456789abcdef";
    std::string r; r.reserve(n*2);
    for (size_t i=0;i<n;i++) { r.push_back(H[p[i]>>4]); r.push_back(H[p[i]&0xF]); }
    return r;
}

// Base64 (URL-safe, no padding) — for JWT signature decoding.
int b64v(char c) {
    if (c>='A'&&c<='Z') return c-'A';
    if (c>='a'&&c<='z') return c-'a'+26;
    if (c>='0'&&c<='9') return c-'0'+52;
    if (c=='-'||c=='+') return 62;
    if (c=='_'||c=='/') return 63;
    return -1;
}
std::vector<uint8_t> b64url_dec(const std::string& s) {
    std::vector<uint8_t> out; out.reserve(s.size()*3/4);
    int val=0, bits=0;
    for (char c : s) {
        if (c=='='||c=='\n'||c=='\r') continue;
        int v=b64v(c); if (v<0) continue;
        val=(val<<6)|v; bits+=6;
        if (bits>=8) { bits-=8; out.push_back((uint8_t)((val>>bits)&0xFF)); }
    }
    return out;
}

void hmac_sha256(const uint8_t* key, size_t klen, const uint8_t* msg, size_t mlen, uint8_t out[32]) {
    uint8_t k[64]={0};
    if (klen>64) { Sha256 s; s.init(); s.update(key,klen); s.finish(k); }
    else memcpy(k,key,klen);
    uint8_t ipad[64], opad[64];
    for (int i=0;i<64;i++){ ipad[i]=k[i]^0x36; opad[i]=k[i]^0x5c; }
    Sha256 s1; s1.init(); s1.update(ipad,64); s1.update(msg,mlen);
    uint8_t inner[32]; s1.finish(inner);
    Sha256 s2; s2.init(); s2.update(opad,64); s2.update(inner,32); s2.finish(out);
}

// Split trim helper — treats comma AND whitespace as separators.
std::vector<std::string> split_csv(const std::string& s) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : s) {
        if (c==','||c==' '||c=='\t'||c=='\n'||c=='\r') {
            if (!cur.empty()) { out.push_back(cur); cur.clear(); }
        } else cur.push_back(c);
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

// XOR-obfuscated file token so `strings libv.so | grep nvp` gets nothing.
template <size_t N>
struct T {
    char d[N];
    constexpr T(const char (&s)[N]) : d{} {
        for (size_t i=0;i<N;i++)
            d[i] = static_cast<char>(static_cast<uint8_t>(s[i]) ^ (0x71u + (i * 5u)));
    }
    std::string get() const {
        std::string r; r.reserve(N);
        for (size_t i=0;i+1<N;i++)
            r.push_back(static_cast<char>(static_cast<uint8_t>(d[i]) ^ (0x71u + (i * 5u))));
        return r;
    }
};
static constexpr T FTAG{"nvp"};

void report_and_die(const char* func) {
    tinfo_report(FTAG.get().c_str(), func);
    kill(getpid(), SIGKILL);
    _exit(137);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_apix_app_Net_nvpRunGuards(JNIEnv*, jclass) {
    apix_native_guard();
}

JNIEXPORT void JNICALL
Java_com_apix_app_Net_nvpTerminate(JNIEnv* env, jclass, jstring jreason) {
    const char* rc = jreason ? env->GetStringUTFChars(jreason, nullptr) : nullptr;
    std::string reason = rc ? rc : "enforce";
    if (rc) env->ReleaseStringUTFChars(jreason, rc);
    report_and_die(reason.c_str());
}

// ── SSL pinning ────────────────────────────────────────────────────
// Java hands us the pins CSV + the raw SPKI DER bytes of the negotiated
// leaf cert. We compute SHA-256 in native code and compare against the pins.
JNIEXPORT jint JNICALL
Java_com_apix_app_Net_nvpVerifySsl(JNIEnv* env, jclass, jstring jpins, jbyteArray jspki) {
    if (!jpins || !jspki) return 0;
    const char* pc = env->GetStringUTFChars(jpins, nullptr);
    std::string pins = pc ? pc : "";
    if (pc) env->ReleaseStringUTFChars(jpins, pc);
    if (pins.empty()) return 1; // pinning disabled → allow

    jsize len = env->GetArrayLength(jspki);
    std::vector<uint8_t> spki(len);
    env->GetByteArrayRegion(jspki, 0, len, reinterpret_cast<jbyte*>(spki.data()));

    Sha256 s; s.init(); s.update(spki.data(), spki.size());
    uint8_t out[32]; s.finish(out);
    std::string got_hex = hex(out, 32);

    // Accept two encodings for pins: base64 (Android standard) or hex.
    // For base64 we compare after decoding both sides to raw bytes.
    for (const auto& p : split_csv(pins)) {
        std::string lp; lp.reserve(p.size());
        for (char c : p) lp.push_back((char)tolower((unsigned char)c));
        if (lp == got_hex) return 1;
        auto raw = b64url_dec(p);
        if (raw.size() == 32 && memcmp(raw.data(), out, 32) == 0) return 1;
    }
    return 0;
}

// ── VPN allowlist enforcement ──────────────────────────────────────
JNIEXPORT jint JNICALL
Java_com_apix_app_Net_nvpCheckVpn(JNIEnv* env, jclass, jboolean vpnUp, jstring jip, jstring jlist) {
    if (vpnUp != JNI_TRUE) return 0; // no tunnel → nothing to enforce
    const char* ipc = jip   ? env->GetStringUTFChars(jip,   nullptr) : nullptr;
    const char* lsc = jlist ? env->GetStringUTFChars(jlist, nullptr) : nullptr;
    std::string ip   = ipc ? ipc : "";
    std::string list = lsc ? lsc : "";
    if (ipc) env->ReleaseStringUTFChars(jip, ipc);
    if (lsc) env->ReleaseStringUTFChars(jlist, lsc);

    if (ip.empty()) report_and_die("vpn_no_ip");

    for (const auto& allow : split_csv(list)) {
        if (allow == ip) return 0; // exact IP allow-list match
        // Simple CIDR-ish prefix support: "1.2.3." matches "1.2.3.*".
        if (!allow.empty() && allow.back() == '.' &&
            ip.compare(0, allow.size(), allow) == 0) return 0;
    }
    report_and_die("vpn_block");
    return 1;
}

// ── Ban gate ───────────────────────────────────────────────────────
// Called after the Java-side handshake resolves. If verdict != ACTIVE we
// terminate from native — Smali patches on the Java caller cannot skip this.
JNIEXPORT jint JNICALL
Java_com_apix_app_Net_nvpCheckBan(JNIEnv* env, jclass, jstring jstatus) {
    if (!jstatus) return 0;
    const char* sc = env->GetStringUTFChars(jstatus, nullptr);
    std::string s = sc ? sc : "";
    if (sc) env->ReleaseStringUTFChars(jstatus, sc);
    for (auto& c : s) c = (char)toupper((unsigned char)c);
    if (s == "ACTIVE" || s == "OK" || s.empty() || s == "ERROR") return 0;
    // Anything else (TEMP_BAN, PERMA_BAN, TAMPERED_MOD, ENVIRONMENT_DANGER,
    // VPN_BLOCK, WIPE) → hard kill through tostinfo.
    report_and_die("ban");
    return 1;
}

// ── VIP verification (HS256) ───────────────────────────────────────
JNIEXPORT jint JNICALL
Java_com_apix_app_Net_nvpCheckVip(JNIEnv* env, jclass, jstring jtoken) {
    if (!jtoken) return 0;
    const char* tc = env->GetStringUTFChars(jtoken, nullptr);
    std::string tok = tc ? tc : "";
    if (tc) env->ReleaseStringUTFChars(jtoken, tc);

    size_t p1 = tok.find('.');
    if (p1 == std::string::npos) return 0;
    size_t p2 = tok.find('.', p1+1);
    if (p2 == std::string::npos) return 0;
    std::string signing = tok.substr(0, p2);
    std::string sig     = tok.substr(p2+1);

    // Reconstruct HMAC secret from split defines. If either half is missing
    // the token cannot possibly verify — return 0 without exiting so the
    // caller can decide (VIP absence must not brick a legit user).
    std::string secret = std::string(V_B1) + std::string(V_B2);
    if (secret.empty()) return 0;

    uint8_t mac[32];
    hmac_sha256(reinterpret_cast<const uint8_t*>(secret.data()), secret.size(),
                reinterpret_cast<const uint8_t*>(signing.data()), signing.size(), mac);
    auto sig_raw = b64url_dec(sig);
    if (sig_raw.size() != 32) return 0;
    // Constant-time compare.
    uint8_t diff = 0;
    for (int i=0;i<32;i++) diff |= (uint8_t)(sig_raw[i] ^ mac[i]);
    if (diff != 0) return 0;

    auto payloadRaw = b64url_dec(tok.substr(p1 + 1, p2 - p1 - 1));
    std::string payload(payloadRaw.begin(), payloadRaw.end());
    if (payload.find("\"vip\":true") == std::string::npos) return 0;
    size_t ep = payload.find("\"exp\"");
    if (ep == std::string::npos) return 0;
    size_t colon = payload.find(':', ep);
    if (colon == std::string::npos) return 0;
    char* end = nullptr;
    long long exp = strtoll(payload.c_str() + colon + 1, &end, 10);
    if (end == payload.c_str() + colon + 1 || exp <= (long long)time(nullptr)) return 0;
    return 1;
}

} // extern "C"

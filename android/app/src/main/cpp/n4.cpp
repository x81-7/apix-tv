#include <jni.h>
#include <string>

// XOR key — مختلف تماماً عن n1/n2/n3
static const uint8_t K4 = 0xA5u;

static std::string n4d(const uint8_t* e, size_t n) {
    std::string r(n, '\0');
    for (size_t i = 0; i < n; i++) r[i] = (char)(e[i] ^ K4);
    return r;
}

// Encrypted "https://"
// h=0x68^A5=CD, t=0x74^A5=D1, t=D1, p=0x70^A5=D5
// s=0x73^A5=D6, :=0x3A^A5=9F, /=0x2F^A5=8A, /=8A
static const uint8_t E_HTTPS[] = {0xCD,0xD1,0xD1,0xD5,0xD6,0x9F,0x8A,0x8A};

// Encrypted "t.me/"
// t=D1, .=0x2E^A5=8B, m=0x6D^A5=C8, e=0x65^A5=C0, /=8A
static const uint8_t E_TGME[]  = {0xD1,0x8B,0xC8,0xC0,0x8A};

// Encrypted ":shield"
// :=9F, s=D6, h=CD, i=CC, e=C0, l=C9, d=C1
static const uint8_t E_PROC[]  = {0x9F,0xD6,0xCD,0xCC,0xC0,0xC9,0xC1};

// Encrypted "TEMP_BAN"
// T=0x54^A5=F1, E=0x45^A5=E0, M=0x4D^A5=E8
// P=0x50^A5=F5, _=0x5F^A5=FA, B=0x42^A5=E7
// A=0x41^A5=E4, N=0x4E^A5=EB
static const uint8_t E_TEMP[]  = {0xF1,0xE0,0xE8,0xF5,0xFA,0xE7,0xE4,0xEB};

// Encrypted "PERMA_BAN"
// P=F5, E=E0, R=0x52^A5=F7, M=E8, A=E4
// _=FA, B=E7, A=E4, N=EB
static const uint8_t E_PERM[]  = {0xF5,0xE0,0xF7,0xE8,0xE4,0xFA,0xE7,0xE4,0xEB};

// Encrypted "ENVIRONMENT_DANGER"
// E=E0, N=EB, V=0x56^A5=F3, I=0x49^A5=EC, R=F7
// O=0x4F^A5=EA, N=EB, M=E8, E=E0, N=EB
// T=F1, _=FA, D=0x44^A5=E1, A=E4, N=EB
// G=0x47^A5=E2, E=E0, R=F7
static const uint8_t E_ENV_D[] = {
    0xE0,0xEB,0xF3,0xEC,0xF7,0xEA,0xEB,0xE8,
    0xE0,0xEB,0xF1,0xFA,0xE1,0xE4,0xEB,0xE2,0xE0,0xF7
};

// Encrypted "EMULATOR"
// E=E0, M=E8, U=0x55^A5=F0, L=0x4C^A5=E9
// A=E4, T=F1, O=EA, R=F7
static const uint8_t E_EMU[]   = {0xE0,0xE8,0xF0,0xE9,0xE4,0xF1,0xEA,0xF7};

// ── JNI Functions ──────────────────────────────────────────────────

// Returns "https://"
extern "C" JNIEXPORT jstring JNICALL
Java_com_apix_app_x_nk1(JNIEnv* env, jclass) {
    return env->NewStringUTF(n4d(E_HTTPS, sizeof(E_HTTPS)).c_str());
}

// Returns "t.me/"
extern "C" JNIEXPORT jstring JNICALL
Java_com_apix_app_x_nk2(JNIEnv* env, jclass) {
    return env->NewStringUTF(n4d(E_TGME, sizeof(E_TGME)).c_str());
}

// Returns ":shield"
extern "C" JNIEXPORT jstring JNICALL
Java_com_apix_app_x_nk3(JNIEnv* env, jclass) {
    return env->NewStringUTF(n4d(E_PROC, sizeof(E_PROC)).c_str());
}

// Returns true if the passed status matches TEMP_BAN
extern "C" JNIEXPORT jboolean JNICALL
Java_com_apix_app_x_nk4(JNIEnv* env, jclass, jstring jStatus) {
    if (!jStatus) return JNI_FALSE;
    const char* s = env->GetStringUTFChars(jStatus, nullptr);
    std::string temp  = n4d(E_TEMP,  sizeof(E_TEMP));
    std::string perm  = n4d(E_PERM,  sizeof(E_PERM));
    std::string envD  = n4d(E_ENV_D, sizeof(E_ENV_D));
    std::string emu   = n4d(E_EMU,   sizeof(E_EMU));
    bool isBanned = (temp == s || perm == s || envD == s || emu == s);
    env->ReleaseStringUTFChars(jStatus, s);
    return isBanned ? JNI_TRUE : JNI_FALSE;
}
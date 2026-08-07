// tostinfo.cpp — smart debug-only kill toast dispatcher.
//
// Design contract (must be honored):
//   * Release APK (BuildConfig.DEBUG == false) → always `_exit(0)` silently,
//     regardless of any stored flag. Enforced by Java-side gate + native gate.
//   * Debug APK + control-panel toggle OFF → `_exit(0)` silently.
//   * Debug APK + control-panel toggle ON  → show a native toast with
//     "[file] :: [func]" then sleep 5 seconds, then `_exit(0)`.
//
// Every security surface (nvp.cpp, sec.cpp, n1/n2/n3.cpp, g1/g2/g3.java …)
// funnels detections through this single entry point instead of calling
// `Toast.makeText(...)` or `_exit()` directly.
//
// The toggle is pushed from Java via `TostInfo.setDebugEnabled(...)`.
// Native code has NO access to SharedPreferences and does NOT fetch the flag
// from any string source — this stops a hooker from spoofing the state
// through a hooked prefs call.

#include <jni.h>
#include <string>
#include <atomic>
#include <cstdlib>
#include <unistd.h>
#include <pthread.h>

namespace {
    // Runtime gate. Java sets this from BuildConfig.DEBUG && admin toggle.
    // Default = false ensures a Release build never leaks a toast even if
    // the Java setter is never invoked.
    std::atomic<bool> g_debug_enabled{false};

    // Compile-time XOR obfuscation to keep the toast prefix off .rodata.
    template <size_t N>
    struct O {
        char d[N];
        constexpr O(const char (&s)[N]) : d{} {
            for (size_t i = 0; i < N; i++)
                d[i] = static_cast<char>(static_cast<uint8_t>(s[i]) ^ (0x3Au + (i * 11u)));
        }
        std::string dec() const {
            std::string r; r.reserve(N);
            for (size_t i = 0; i + 1 < N; i++)
                r.push_back(static_cast<char>(static_cast<uint8_t>(d[i]) ^ (0x3Au + (i * 11u))));
            return r;
        }
    };

    static constexpr O PFX{"⚠ APiX Guard: "};
    static constexpr O ARR{" :: "};

    // Cached VM pointer for background-thread toast dispatch.
    JavaVM* g_vm = nullptr;
    jclass  g_tost_cls = nullptr; // com/apix/app/security/TostInfo
    jmethodID g_show_mid = nullptr; // static showToastStatic(String)
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// Java setter: TostInfo.jniSetDebugEnabled(true/false)
extern "C" JNIEXPORT void JNICALL
Java_com_apix_app_security_TostInfo_jniSetDebugEnabled(JNIEnv*, jclass, jboolean enabled) {
    g_debug_enabled.store(enabled == JNI_TRUE);
}

// Java bootstrap so we can call back into TostInfo.showToastStatic() safely.
extern "C" JNIEXPORT void JNICALL
Java_com_apix_app_security_TostInfo_jniBind(JNIEnv* env, jclass cls) {
    if (g_tost_cls == nullptr) {
        g_tost_cls = reinterpret_cast<jclass>(env->NewGlobalRef(cls));
        g_show_mid = env->GetStaticMethodID(cls, "showToastStatic", "(Ljava/lang/String;)V");
    }
}

// Internal: called from any native security file when a detection fires.
// `file`/`func` are compile-time-known short tokens (already XOR-obfuscated
// by their call sites), never full paths.
static void tinfo_dispatch(const char* file, const char* func) {
    if (!g_debug_enabled.load()) {
        _exit(0);
    }
    if (g_vm == nullptr || g_tost_cls == nullptr || g_show_mid == nullptr) {
        _exit(0);
    }
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) _exit(0);
        attached = true;
    }
    std::string msg = PFX.dec();
    msg += (file ? file : "?");
    msg += ARR.dec();
    msg += (func ? func : "?");
    jstring js = env->NewStringUTF(msg.c_str());
    env->CallStaticVoidMethod(g_tost_cls, g_show_mid, js);
    env->DeleteLocalRef(js);
    if (attached) g_vm->DetachCurrentThread();
    // Give the toast 5 seconds on-screen, then terminate.
    sleep(5);
    _exit(0);
}

// Public C entry the other cpp files (nvp/sec/n1/n2/n3) link against.
extern "C" void tinfo_report(const char* file, const char* func) {
    tinfo_dispatch(file, func);
}

// JNI entry the Java layer (g1/g2/g3/SplashActivity) calls into.
// Signature: TostInfo.jniReport(String file, String func)
extern "C" JNIEXPORT void JNICALL
Java_com_apix_app_security_TostInfo_jniReport(JNIEnv* env, jclass, jstring jfile, jstring jfunc) {
    const char* f = jfile ? env->GetStringUTFChars(jfile, nullptr) : nullptr;
    const char* n = jfunc ? env->GetStringUTFChars(jfunc, nullptr) : nullptr;
    // Copy into local buffers so we can safely release the JNI strings before
    // sleeping in dispatch().
    std::string ff = f ? f : "";
    std::string nn = n ? n : "";
    if (f) env->ReleaseStringUTFChars(jfile, f);
    if (n) env->ReleaseStringUTFChars(jfunc, n);
    tinfo_dispatch(ff.c_str(), nn.c_str());
}

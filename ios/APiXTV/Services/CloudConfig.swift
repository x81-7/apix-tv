import Foundation

enum CloudConfig {
    // Lovable Cloud (NEW project, post-migration 2026-04-29).
    // Keep in sync with android/app/build.gradle CLOUD_URL / CLOUD_ANON_KEY.
    // CI/Xcode can override via Info.plist keys APIX_CLOUD_URL / APIX_CLOUD_ANON_KEY.
    //
    // White-label gateway: when APIX_WORKER_URL is provided the app talks ONLY
    // to the Cloudflare Worker (which hides Supabase). It proxies the identical
    // /rest/v1 and /functions/v1 paths, so only the base origin changes.
    static let baseURL: URL = {
        if let w = Bundle.main.object(forInfoDictionaryKey: "APIX_WORKER_URL") as? String,
           !w.isEmpty, let u = URL(string: w) { return u }
        if let v = Bundle.main.object(forInfoDictionaryKey: "APIX_CLOUD_URL") as? String,
           let u = URL(string: v) { return u }
        // Gateway-only: no hardcoded origin. Configure APIX_WORKER_URL / APIX_CLOUD_URL.
        return URL(string: "https://127.0.0.1")!
    }()
    static let anonKey: String = {
        if let v = Bundle.main.object(forInfoDictionaryKey: "APIX_CLOUD_ANON_KEY") as? String,
           !v.isEmpty { return v }
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZlZnJwdGZncWtpaXdmcWNqeGJnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc0Njc4NTUsImV4cCI6MjA5MzA0Mzg1NX0.vOGObG_IuNlf6oieE1vK0JdeERn_XWzFLqIDIAFgZow"
    }()
}

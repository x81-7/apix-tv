import Foundation

enum CloudConfig {
    // Points to the Lovable Cloud project where the admin panel writes data.
    // Keep in sync with android/app/build.gradle CLOUD_URL / CLOUD_ANON_KEY
    // and windows/app/.../SupabaseClient.kt DEFAULT_URL / DEFAULT_KEY.
    static let baseURL = URL(string: "https://xfrcjwybxftxspvpegfb.supabase.co")!
    static let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhmcmNqd3lieGZ0eHNwdnBlZ2ZiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzczNzkyMzksImV4cCI6MjA5Mjk1NTIzOX0.xtoVGdA1zNJBRKerY16azg8NQSMXwK6Xmid7TERKAR0"
}

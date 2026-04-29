import SwiftUI

/// Boot splash — APiX wordmark + gold spinner. Mirrors Android SplashActivity
/// and Windows SplashScreen so the launch experience is identical across
/// platforms.
struct SplashView: View {
    @State private var pulse = false

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            VStack(spacing: 28) {
                HStack(spacing: 0) {
                    Text("AP")
                        .foregroundStyle(.white)
                        .font(.system(size: 64, weight: .heavy))
                    Text("iX")
                        .foregroundStyle(AppTheme.gold)
                        .font(.system(size: 64, weight: .heavy))
                }
                .scaleEffect(pulse ? 1.05 : 0.95)
                .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: pulse)

                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(AppTheme.gold)
                    .scaleEffect(1.4)
            }
        }
        .onAppear { pulse = true }
    }
}
import SwiftUI
import UIKit

/// Hides app content while the screen is being recorded or captured.
/// Mirrors Android's `FLAG_SECURE`. Apple does not allow blocking the
/// recording itself, but we can blur/blank the UI so nothing useful is
/// captured.
final class ScreenCaptureMonitor: ObservableObject {
    @Published var isCaptured: Bool = UIScreen.main.isCaptured

    private var observer: NSObjectProtocol?

    init() {
        observer = NotificationCenter.default.addObserver(
            forName: UIScreen.capturedDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.isCaptured = UIScreen.main.isCaptured
        }
    }

    deinit {
        if let o = observer { NotificationCenter.default.removeObserver(o) }
    }
}

struct ScreenCaptureGuard: ViewModifier {
    @StateObject private var monitor = ScreenCaptureMonitor()

    func body(content: Content) -> some View {
        ZStack {
            content
            if monitor.isCaptured {
                ZStack {
                    Color.black.ignoresSafeArea()
                    VStack(spacing: 12) {
                        Image(systemName: "eye.slash.fill")
                            .font(.system(size: 48, weight: .bold))
                            .foregroundStyle(.white)
                        Text("تم إيقاف العرض أثناء تسجيل الشاشة")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                    }
                    .padding(24)
                }
                .transition(.opacity)
            }
        }
    }
}

extension View {
    /// Apply on the root view to blank UI during screen recording / mirroring.
    func protectedFromScreenCapture() -> some View {
        modifier(ScreenCaptureGuard())
    }
}
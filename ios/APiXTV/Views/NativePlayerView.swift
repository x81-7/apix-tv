import SwiftUI
import AVKit
import Combine

// MARK: - Coordinator (مع إصلاح الصوت والتزامن)
@MainActor
final class NativePlayerCoordinator: ObservableObject {

    let player = AVPlayer()
    private var timeObserver: Any?
    private var statusObserver: AnyCancellable?
    private var itemObserver: AnyCancellable?
    
    @Published var isPlaying      = false
    @Published var isBuffering    = true
    @Published var currentTime: Double = 0
    @Published var duration: Double    = 0
    @Published var errorMessage: String?
    @Published var title = ""

    init() {
        // تفعيل الصوت الإجباري حتى لو كان الهاتف على الوضع الصامت
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Failed to set audio session category.")
        }
    }

    func setupFromApix(_ cfg: ApixResolvedConfig, fallbackTitle: String) {
        title = cfg.title ?? fallbackTitle
        loadURL(cfg.url, headers: cfg.headers)
    }

    private func loadURL(_ urlString: String, headers: [String: String]) {
        forceCleanupObservers()
        errorMessage = nil
        isBuffering  = true
        currentTime  = 0
        duration     = 0

        guard let url = URL(string: urlString) else {
            errorMessage = "رابط غير صالح"; return
        }
        
        var opts: [String: Any] = [:]
        if !headers.isEmpty { opts["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        
        let asset = AVURLAsset(url: url, options: opts.isEmpty ? nil : opts)
        let item = AVPlayerItem(asset: asset)
        
        player.replaceCurrentItem(with: item)
        attachObservers(item: item)
        player.play()
        isPlaying = true
    }

    func togglePlay() {
        if isPlaying { player.pause() } else { player.play() }
        isPlaying.toggle()
    }

    func seek(by seconds: Double) {
        let target = CMTime(seconds: currentTime + seconds, preferredTimescale: 600)
        player.seek(to: target)
    }

    func seek(to fraction: Double) {
        let secs = duration * fraction
        let target = CMTime(seconds: secs, preferredTimescale: 600)
        player.seek(to: target)
    }

    private func attachObservers(item: AVPlayerItem) {
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            guard let self = self else { return }
            self.currentTime = time.seconds
            // جلب المدة الحقيقية للبث أو الفيديو
            if let duration = item.duration.seconds.isFinite ? item.duration.seconds : nil {
                self.duration = duration
            }
        }

        itemObserver = item.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self, weak item] status in
                switch status {
                case .readyToPlay: 
                    self?.isBuffering = false
                case .failed:
                    self?.errorMessage = item?.error?.localizedDescription ?? "فشل التشغيل"
                    self?.isBuffering  = false
                default: break
                }
            }

        NotificationCenter.default.addObserver(forName: .AVPlayerItemPlaybackStalled, object: item, queue: .main) { [weak self] _ in
            self?.isBuffering = true
        }
        
        // اكتشاف عودة التشغيل بعد التقطيع
        player.publisher(for: \.timeControlStatus)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                if status == .playing { self?.isBuffering = false }
            }
            .store(in: &itemObserver!)
    }

    private func forceCleanupObservers() {
        if let obs = timeObserver { player.removeTimeObserver(obs); timeObserver = nil }
        itemObserver?.cancel()
        statusObserver?.cancel()
        NotificationCenter.default.removeObserver(self)
    }

    func cleanupAll() {
        forceCleanupObservers()
        player.pause()
    }
}

// MARK: - Video Layer
struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let resizeMode: AVLayerVideoGravity

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = resizeMode
        view.layer.addSublayer(layer)
        context.coordinator.playerLayer = layer
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.playerLayer?.frame = uiView.bounds
        context.coordinator.playerLayer?.videoGravity = resizeMode
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    class Coordinator { var playerLayer: AVPlayerLayer? }
}

// MARK: - Main Player View (تصميم الأندرويد)
struct NativePlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var coordinator = NativePlayerCoordinator()
    
    // محاكاة المتغيرات للاختبار
    let testUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    
    @State private var showControls      = true
    @State private var controlsTimer: Timer?
    @State private var resizeMode: AVLayerVideoGravity = .resizeAspect

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            PlayerVideoLayer(player: coordinator.player, resizeMode: resizeMode)
                .ignoresSafeArea()

            if coordinator.isBuffering && coordinator.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Color(red: 229/255, green: 9/255, blue: 20/255)) // أحمر مثل الأندرويد
                    .scaleEffect(1.8)
            }

            if showControls && coordinator.errorMessage == nil {
                controlsOverlay
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { toggleControlsVisibility() }
        .statusBarHidden(true)
        .preferredColorScheme(.dark)
        .onAppear {
            forceLandscape()
            // محاكاة وضع رابط للاختبار (استبدله بالمتغيرات القادمة من Apix)
            coordinator.setupFromApix(ApixResolvedConfig(url: testUrl, backupUrl: nil, headers: [:], player: "native"), fallbackTitle: "بث تجريبي")
        }
        .onDisappear {
            forcePortrait()
            coordinator.cleanupAll()
        }
    }

    private func forceLandscape() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape))
    }

    private func forcePortrait() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait))
    }

    // MARK: - تصميم الواجهة مطابق 100% للأندرويد
    private var controlsOverlay: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.8), .clear], startPoint: .top, endPoint: .bottom).frame(height: 100)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom).frame(height: 120)
            }.ignoresSafeArea()

            VStack {
                HStack {
                    Button(action: { coordinator.player.pause(); dismiss() }) {
                        CustomIcon(type: .back)
                    }
                    Spacer()
                    Text(coordinator.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 24).padding(.top, 16)

                Spacer()

                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        Text(formatTime(coordinator.currentTime))
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        
                        // شريط تقدم سلس ومطابق للأندرويد
                        Slider(
                            value: Binding(
                                get: { coordinator.duration > 0 ? coordinator.currentTime / coordinator.duration : 0 },
                                set: { coordinator.seek(to: $0) }
                            ),
                            in: 0...1
                        )
                        .tint(Color(red: 229/255, green: 9/255, blue: 20/255))
                        
                        Text(formatTime(coordinator.duration))
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                    }

                    HStack {
                        HStack(spacing: 24) {
                            Button(action: { coordinator.seek(by: -10); resetControlsTimer() }) {
                                CustomIcon(type: .rewind)
                            }
                            Button(action: { coordinator.togglePlay(); resetControlsTimer() }) {
                                CustomIcon(type: coordinator.isPlaying ? .pause : .play, isLarge: true)
                            }
                            Button(action: { coordinator.seek(by: 10); resetControlsTimer() }) {
                                CustomIcon(type: .forward)
                            }
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 20) {
                            Button(action: { 
                                resizeMode = resizeMode == .resizeAspect ? .resizeAspectFill : .resizeAspect 
                            }) {
                                CustomIcon(type: .resize)
                            }
                        }
                    }
                }
                .padding(.horizontal, 24).padding(.bottom, 24)
            }
        }
    }

    private func toggleControlsVisibility() {
        withAnimation(.easeInOut(duration: 0.2)) { showControls.toggle() }
        if showControls { resetControlsTimer() }
    }

    private func resetControlsTimer() {
        controlsTimer?.invalidate()
        controlsTimer = Timer.scheduledTimer(withTimeInterval: 4, repeats: false) { _ in
            withAnimation(.easeOut(duration: 0.3)) { showControls = false }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "00:00" }
        let h = Int(seconds) / 3600
        let m = (Int(seconds) % 3600) / 60
        let s = Int(seconds) % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }
}

// MARK: - رسم الأيقونات المطابقة للأندرويد 100%
enum IconType { case play, pause, forward, rewind, back, resize }

struct CustomIcon: View {
    let type: IconType
    var isLarge: Bool = false
    
    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white, lineWidth: isLarge ? 2 : 1.5)
                .background(Circle().fill(Color.black.opacity(0.3)))
                .frame(width: isLarge ? 56 : 44, height: isLarge ? 56 : 44)
            
            iconPath
                .stroke(Color.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                .frame(width: 24, height: 24)
        }
    }
    
    @ViewBuilder
    var iconPath: some View {
        switch type {
        case .play:
            Path { p in
                p.move(to: CGPoint(x: 9, y: 6))
                p.addLine(to: CGPoint(x: 9, y: 18))
                p.addLine(to: CGPoint(x: 18, y: 12))
                p.closeSubpath()
            }
        case .pause:
            Path { p in
                p.move(to: CGPoint(x: 8, y: 6)); p.addLine(to: CGPoint(x: 8, y: 18))
                p.move(to: CGPoint(x: 16, y: 6)); p.addLine(to: CGPoint(x: 16, y: 18))
            }
        case .forward:
            Path { p in
                p.move(to: CGPoint(x: 7, y: 7)); p.addLine(to: CGPoint(x: 12, y: 12)); p.addLine(to: CGPoint(x: 7, y: 17))
                p.move(to: CGPoint(x: 13, y: 7)); p.addLine(to: CGPoint(x: 18, y: 12)); p.addLine(to: CGPoint(x: 13, y: 17))
            }
        case .rewind:
            Path { p in
                p.move(to: CGPoint(x: 17, y: 7)); p.addLine(to: CGPoint(x: 12, y: 12)); p.addLine(to: CGPoint(x: 17, y: 17))
                p.move(to: CGPoint(x: 11, y: 7)); p.addLine(to: CGPoint(x: 6, y: 12)); p.addLine(to: CGPoint(x: 11, y: 17))
            }
        case .back:
            Path { p in
                p.move(to: CGPoint(x: 19, y: 12)); p.addLine(to: CGPoint(x: 5, y: 12))
                p.move(to: CGPoint(x: 12, y: 19)); p.addLine(to: CGPoint(x: 5, y: 12)); p.addLine(to: CGPoint(x: 12, y: 5))
            }
        case .resize:
            Path { p in
                p.move(to: CGPoint(x: 15, y: 3)); p.addLine(to: CGPoint(x: 21, y: 3)); p.addLine(to: CGPoint(x: 21, y: 9))
                p.move(to: CGPoint(x: 9, y: 21)); p.addLine(to: CGPoint(x: 3, y: 21)); p.addLine(to: CGPoint(x: 3, y: 15))
                p.move(to: CGPoint(x: 21, y: 3)); p.addLine(to: CGPoint(x: 14, y: 10))
                p.move(to: CGPoint(x: 3, y: 21)); p.addLine(to: CGPoint(x: 10, y: 14))
            }
        }
    }
}

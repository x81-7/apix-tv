import SwiftUI
import AVKit
import Combine

// MARK: - 1. Player Coordinator (إدارة محرك AVPlayer والتزامن)
@MainActor
final class ApixPlayerCoordinator: ObservableObject {
    let player = AVPlayer()
    
    @Published var isPlaying = false
    @Published var isBuffering = true
    @Published var currentTime: Double = 0
    @Published var duration: Double = 0
    @Published var errorMessage: String?
    @Published var title = ""
    
    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // فرض تشغيل الصوت وفك قيود الآيفون الصامت
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .moviePlayback, options: [.allowBluetooth, .allowAirPlay])
            try session.setActive(true)
        } catch {
            print("AVAudioSession Setup Failed: \(error.localizedDescription)")
        }
    }
    
    func loadStream(url urlString: String, title: String, headers: [String: String]) {
        self.title = title
        cleanup()
        isBuffering = true
        errorMessage = nil
        
        guard let url = URL(string: urlString) else {
            errorMessage = "الرابط غير صالح"
            return
        }
        
        // تمرير الهيدرز (اليوزر ايجنت وغيره)
        var options: [String: Any] = [:]
        if !headers.isEmpty {
            options["AVURLAssetHTTPHeaderFieldsKey"] = headers
        }
        
        let asset = AVURLAsset(url: url, options: options)
        let item = AVPlayerItem(asset: asset)
        
        player.replaceCurrentItem(with: item)
        setupObservers(for: item)
        player.play()
    }
    
    private func setupObservers(for item: AVPlayerItem) {
        // مراقبة حالة التحميل
        item.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                switch status {
                case .readyToPlay:
                    self?.isBuffering = false
                    self?.duration = item.duration.seconds.isNaN ? 0 : item.duration.seconds
                case .failed:
                    self?.errorMessage = item.error?.localizedDescription ?? "حدث خطأ أثناء التشغيل"
                    self?.isBuffering = false
                default: break
                }
            }.store(in: &cancellables)
        
        // مراقبة حالة التقطيع (Stalled)
        NotificationCenter.default.publisher(for: .AVPlayerItemPlaybackStalled, object: item)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.isBuffering = true }
            .store(in: &cancellables)
        
        // مراقبة التشغيل والإيقاف المؤقت الفعلي
        player.publisher(for: \.timeControlStatus)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                self?.isPlaying = (status == .playing)
                if status == .playing { self?.isBuffering = false }
            }.store(in: &cancellables)
        
        // تحديث شريط التقدم كل نصف ثانية
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            guard let self = self else { return }
            if self.player.timeControlStatus == .playing {
                self.currentTime = time.seconds
            }
        }
    }
    
    func togglePlay() {
        if player.timeControlStatus == .playing { player.pause() } else { player.play() }
    }
    
    func seek(by seconds: Double) {
        let target = CMTime(seconds: currentTime + seconds, preferredTimescale: 600)
        player.seek(to: target)
    }
    
    func seekTo(fraction: Double) {
        guard duration > 0 else { return }
        let targetTime = duration * fraction
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        player.seek(to: target) { [weak self] _ in
            self?.currentTime = targetTime
        }
    }
    
    func cleanup() {
        if let obs = timeObserver {
            player.removeTimeObserver(obs)
            timeObserver = nil
        }
        cancellables.removeAll()
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
}

// MARK: - 2. Video Layer (طبقة العرض)
struct ApixVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let resizeMode: AVLayerVideoGravity

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = resizeMode
        view.layer.addSublayer(layer)
        context.coordinator.layer = layer
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.layer?.frame = uiView.bounds
        context.coordinator.layer?.videoGravity = resizeMode
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    class Coordinator { var layer: AVPlayerLayer? }
}

// MARK: - 3. Main Player View (الواجهة الرئيسية)
struct ApixPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var coordinator = ApixPlayerCoordinator()
    
    // المتغيرات القادمة من الـ Resolver
    let streamUrl: String
    let streamTitle: String
    let streamHeaders: [String: String]
    
    @State private var showControls = true
    @State private var resizeMode: AVLayerVideoGravity = .resizeAspect
    @State private var hideTimer: Timer?
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            // 1. طبقة الفيديو
            ApixVideoLayer(player: coordinator.player, resizeMode: resizeMode)
                .ignoresSafeArea()
            
            // 2. مؤشر التحميل
            if coordinator.isBuffering && coordinator.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Color(red: 229/255, green: 9/255, blue: 20/255)) // الأحمر
                    .scaleEffect(1.8)
            }
            
            // 3. رسالة الخطأ
            if let error = coordinator.errorMessage {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 50)).foregroundColor(.red)
                    Text(error).foregroundColor(.white).multilineTextAlignment(.center).padding(.horizontal)
                    Button("رجوع") { dismiss() }
                        .padding(.horizontal, 30).padding(.vertical, 10)
                        .background(Color.white.opacity(0.2)).cornerRadius(8).foregroundColor(.white)
                }
                .background(Color.black.opacity(0.8)).ignoresSafeArea()
            }
            
            // 4. طبقة التحكم (تطابق الأندرويد)
            if showControls && coordinator.errorMessage == nil {
                controlsOverlay
            }
        }
        .onTapGesture { toggleControls() }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            forceLandscape()
            coordinator.loadStream(url: streamUrl, title: streamTitle, headers: streamHeaders)
            startTimer()
        }
        .onDisappear {
            forcePortrait()
            coordinator.cleanup()
            hideTimer?.invalidate()
        }
    }
    
    private var controlsOverlay: some View {
        ZStack {
            // التدرجات اللونية (Gradients)
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom).frame(height: 100)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom).frame(height: 130)
            }.ignoresSafeArea()
            
            // الأزرار والنصوص
            VStack {
                // الشريط العلوي
                HStack {
                    ApixIconButton(type: .back, size: 36) { dismiss() }
                    Spacer()
                    Text(coordinator.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                }
                .padding(.horizontal, 24).padding(.top, 16)
                
                Spacer()
                
                // الشريط السفلي (التقدم + الأزرار)
                VStack(spacing: 8) {
                    // شريط التقدم والوقت
                    HStack(spacing: 12) {
                        Text(formatTime(coordinator.currentTime)).font(.system(size: 14, weight: .medium)).foregroundColor(.white)
                        
                        ApixSlider(
                            value: coordinator.duration > 0 ? coordinator.currentTime / coordinator.duration : 0,
                            onScrubbing: { fraction in
                                startTimer()
                                coordinator.seekTo(fraction: fraction)
                            }
                        )
                        .frame(height: 16)
                        
                        Text(formatTime(coordinator.duration)).font(.system(size: 14, weight: .medium)).foregroundColor(.white)
                    }
                    
                    Spacer().frame(height: 4)
                    
                    // أزرار التحكم
                    HStack {
                        HStack(spacing: 24) {
                            ApixIconButton(type: .rewind, size: 38) { coordinator.seek(by: -10); startTimer() }
                            ApixIconButton(type: coordinator.isPlaying ? .pause : .play, size: 44, isLarge: true) { coordinator.togglePlay(); startTimer() }
                            ApixIconButton(type: .forward, size: 38) { coordinator.seek(by: 10); startTimer() }
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 20) {
                            ApixIconButton(type: .resize, size: 32) {
                                resizeMode = (resizeMode == .resizeAspect) ? .resizeAspectFill : .resizeAspect
                                startTimer()
                            }
                        }
                    }
                }
                .padding(.horizontal, 24).padding(.bottom, 24)
            }
        }
        .transition(.opacity)
    }
    
    // دوال مساعدة
    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) { showControls.toggle() }
        if showControls { startTimer() }
    }
    
    private func startTimer() {
        hideTimer?.invalidate()
        hideTimer = Timer.scheduledTimer(withTimeInterval: 3.5, repeats: false) { _ in
            withAnimation(.easeInOut(duration: 0.3)) { showControls = false }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "00:00" }
        let h = Int(seconds) / 3600
        let m = (Int(seconds) % 3600) / 60
        let s = Int(seconds) % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }
    
    private func forceLandscape() {
        if #available(iOS 16.0, *) {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape))
        } else {
            UIDevice.current.setValue(UIInterfaceOrientation.landscapeRight.rawValue, forKey: "orientation")
        }
    }
    
    private func forcePortrait() {
        if #available(iOS 16.0, *) {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait))
        } else {
            UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
        }
    }
}

// MARK: - 4. Custom Slider (لحل مشكلة ارتداد الشريط)
struct ApixSlider: View {
    let value: Double
    let onScrubbing: (Double) -> Void
    
    @State private var dragValue: Double? = nil
    
    var body: some View {
        GeometryReader { geo in
            let displayValue = dragValue ?? value
            
            ZStack(alignment: .leading) {
                // الخلفية الشفافة
                Capsule().fill(Color.white.opacity(0.25)).frame(height: 4)
                
                // الشريط الأحمر الممتلئ (يطابق الأندرويد)
                Capsule()
                    .fill(Color(red: 229/255, green: 9/255, blue: 20/255))
                    .frame(width: max(0, geo.size.width * CGFloat(displayValue)), height: 4)
                
                // النقطة البيضاء
                Circle()
                    .fill(Color.white)
                    .frame(width: 14, height: 14)
                    .offset(x: max(0, min(geo.size.width - 14, (geo.size.width * CGFloat(displayValue)) - 7)))
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        let fraction = min(max(0, v.location.x / geo.size.width), 1)
                        dragValue = fraction
                    }
                    .onEnded { v in
                        let fraction = min(max(0, v.location.x / geo.size.width), 1)
                        onScrubbing(fraction)
                        dragValue = nil
                    }
            )
        }
    }
}

// MARK: - 5. Custom Icons (رسم دقيق مطابق لـ Compose ImageVector)
enum ApixIconType { case play, pause, forward, rewind, back, resize }

struct ApixIconButton: View {
    let type: ApixIconType
    var size: CGFloat = 44
    var isLarge: Bool = false
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            ZStack {
                // الدائرة الخارجية (Stroke + Background)
                Circle()
                    .stroke(Color.white, lineWidth: isLarge ? 2 : 1.5)
                    .background(Circle().fill(Color.black.opacity(0.1)))
                    .frame(width: size, height: size)
                
                // مسار الرسمة المخصص (Paths)
                IconPath(type: type)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: 24, height: 24)
            }
            .contentShape(Circle())
        }
    }
}

struct IconPath: Shape {
    let type: ApixIconType
    
    func path(in rect: CGRect) -> Path {
        var p = Path()
        // الحجم الافتراضي لـ Viewport في الأندرويد كان 24x24
        let scaleX = rect.width / 24
        let scaleY = rect.height / 24
        
        switch type {
        case .play:
            p.move(to: CGPoint(x: 8 * scaleX, y: 6 * scaleY))
            p.addLine(to: CGPoint(x: 8 * scaleX, y: 18 * scaleY))
            p.addLine(to: CGPoint(x: 18 * scaleX, y: 12 * scaleY))
            p.closeSubpath()
            
        case .pause:
            // العمود الأيسر
            p.move(to: CGPoint(x: 6 * scaleX, y: 7 * scaleY))
            p.addArc(center: CGPoint(x: 8 * scaleX, y: 7 * scaleY), radius: 2 * scaleX, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            p.addLine(to: CGPoint(x: 10 * scaleX, y: 17 * scaleY))
            // تبسيط المسار ليتطابق مع شكل الإيقاف
            p = Path()
            p.move(to: CGPoint(x: 8 * scaleX, y: 6 * scaleY)); p.addLine(to: CGPoint(x: 8 * scaleX, y: 18 * scaleY))
            p.move(to: CGPoint(x: 16 * scaleX, y: 6 * scaleY)); p.addLine(to: CGPoint(x: 16 * scaleX, y: 18 * scaleY))
            
        case .forward:
            p.move(to: CGPoint(x: 9 * scaleX, y: 7 * scaleY)); p.addLine(to: CGPoint(x: 14 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 9 * scaleX, y: 17 * scaleY))
            p.move(to: CGPoint(x: 15 * scaleX, y: 7 * scaleY)); p.addLine(to: CGPoint(x: 20 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 15 * scaleX, y: 17 * scaleY))
            
        case .rewind:
            p.move(to: CGPoint(x: 15 * scaleX, y: 7 * scaleY)); p.addLine(to: CGPoint(x: 10 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 15 * scaleX, y: 17 * scaleY))
            p.move(to: CGPoint(x: 9 * scaleX, y: 7 * scaleY)); p.addLine(to: CGPoint(x: 4 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 9 * scaleX, y: 17 * scaleY))
            
        case .back:
            p.move(to: CGPoint(x: 19 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 5 * scaleX, y: 12 * scaleY))
            p.move(to: CGPoint(x: 12 * scaleX, y: 19 * scaleY)); p.addLine(to: CGPoint(x: 5 * scaleX, y: 12 * scaleY)); p.addLine(to: CGPoint(x: 12 * scaleX, y: 5 * scaleY))
            
        case .resize:
            p.move(to: CGPoint(x: 15 * scaleX, y: 3 * scaleY)); p.addLine(to: CGPoint(x: 21 * scaleX, y: 3 * scaleY)); p.addLine(to: CGPoint(x: 21 * scaleX, y: 9 * scaleY))
            p.move(to: CGPoint(x: 9 * scaleX, y: 21 * scaleY)); p.addLine(to: CGPoint(x: 3 * scaleX, y: 21 * scaleY)); p.addLine(to: CGPoint(x: 3 * scaleX, y: 15 * scaleY))
            p.move(to: CGPoint(x: 21 * scaleX, y: 3 * scaleY)); p.addLine(to: CGPoint(x: 14 * scaleX, y: 10 * scaleY))
            p.move(to: CGPoint(x: 3 * scaleX, y: 21 * scaleY)); p.addLine(to: CGPoint(x: 10 * scaleX, y: 14 * scaleY))
        }
        return p
    }
}

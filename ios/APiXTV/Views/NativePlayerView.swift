import SwiftUI
import AVKit
import Combine

// MARK: - Constants (Android Matching)
let colorGold = Color(red: 212/255, green: 160/255, blue: 23/255) // #D4A017
let colorRed = Color(red: 229/255, green: 9/255, blue: 20/255)    // #E50914
let colorBgDark = Color(red: 17/255, green: 17/255, blue: 17/255) // #111111
let colorRowSelected = Color(red: 42/255, green: 42/255, blue: 42/255) // #2A2A2A
let colorDivider = Color(red: 34/255, green: 34/255, blue: 34/255) // #222222

// MARK: - Models
struct PlayerServer: Identifiable {
    let id = UUID()
    let name: String
    let url: String
    let headers: [String: String]
    let clearKey: String?
}

struct PlayerQuality: Identifiable {
    let id = UUID()
    let label: String
    let bitrate: Double
}

struct PlayerAudioSource: Identifiable {
    let id = UUID()
    let name: String
    let url: String
}

// MARK: - Icons Enum
enum PlayerIconType {
    case play
    case pause
    case forward
    case rewind
    case back
    case resize
    case settings
    case server
    case pip
}

// MARK: - Coordinator
@MainActor
final class NativePlayerCoordinator: ObservableObject {
    let player = AVPlayer()
    
    @Published var isPlaying = false
    @Published var isBuffering = true
    @Published var currentTime: Double = 0
    @Published var duration: Double = 0
    @Published var errorMessage: String?
    @Published var title = ""
    
    @Published var servers: [PlayerServer] = []
    @Published var currentServerIndex = 0
    
    @Published var qualities: [PlayerQuality] = [
        PlayerQuality(label: "تلقائي", bitrate: 0),
        PlayerQuality(label: "4K", bitrate: 20_000_000),
        PlayerQuality(label: "1080p", bitrate: 8_000_000),
        PlayerQuality(label: "720p", bitrate: 4_000_000),
        PlayerQuality(label: "480p", bitrate: 1_500_000),
        PlayerQuality(label: "360p", bitrate: 800_000)
    ]
    @Published var currentQualityIndex = 0
    @Published var audioSources: [PlayerAudioSource] = []
    
    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .moviePlayback)
            try session.setActive(true)
        } catch {
            print("AVAudioSession Setup Failed: \(error.localizedDescription)")
        }
    }
    
    func setup(channel: Channel) {
        self.title = channel.name
        var newServers: [PlayerServer] = []
        
        let primaryUrlString = channel.playbackURL ?? ""
        if !primaryUrlString.isEmpty {
            newServers.append(PlayerServer(name: "السيرفر الأساسي", url: primaryUrlString, headers: channel.effectiveHeaders, clearKey: channel.clearKeyCombined))
        }
        
        if let backupUrlString = channel.backupURL?.absoluteString, !backupUrlString.isEmpty {
            newServers.append(PlayerServer(name: "السيرفر الاحتياطي", url: backupUrlString, headers: channel.effectiveHeaders, clearKey: channel.clearKeyCombined))
        }
        
        self.servers = newServers
        self.audioSources = []
        
        if !self.servers.isEmpty {
            self.loadServer(index: 0)
        } else {
            self.errorMessage = "No valid URLs found"
            self.isBuffering = false
        }
    }

    func setupFromApix(_ config: ApixResolvedConfig, fallbackTitle: String) {
        self.title = config.title ?? fallbackTitle
        var newServers: [PlayerServer] = [
            PlayerServer(name: "السيرفر الأساسي", url: config.url, headers: config.headers, clearKey: config.clearKey)
        ]
        
        if let backupUrlString = config.backupUrl, !backupUrlString.isEmpty {
            newServers.append(PlayerServer(name: "السيرفر الاحتياطي", url: backupUrlString, headers: config.headers, clearKey: config.clearKey))
        }
        
        self.servers = newServers
        self.audioSources = []
        self.loadServer(index: 0)
    }
    
    func loadServer(index: Int) {
        guard servers.indices.contains(index) else { return }
        self.currentServerIndex = index
        let selectedServer = servers[index]
        self.load(url: selectedServer.url, headers: selectedServer.headers, clearKey: selectedServer.clearKey)
    }
    
    func setQuality(index: Int) {
        self.currentQualityIndex = index
        self.player.currentItem?.preferredPeakBitRate = qualities[index].bitrate
    }
    
    func load(url: String, headers: [String: String], clearKey: String?) {
        self.cleanupAll()
        self.errorMessage = nil
        self.isBuffering = true
        self.currentTime = 0
        self.duration = 0
        
        guard let validUrl = URL(string: url) else {
            self.errorMessage = "Invalid stream URL"
            return
        }
        
        var options: [String: Any] = [:]
        if !headers.isEmpty {
            options["AVURLAssetHTTPHeaderFieldsKey"] = headers
        }
        
        let asset = AVURLAsset(url: validUrl, options: options.isEmpty ? nil : options)
        let item = AVPlayerItem(asset: asset)
        
        self.player.replaceCurrentItem(with: item)
        self.setupObservers(for: item)
        self.player.play()
    }
    
    private func setupObservers(for item: AVPlayerItem) {
        // مراقبة حالة التشغيل
        item.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                guard let self = self else { return }
                switch status {
                case .readyToPlay:
                    self.isBuffering = false
                case .failed:
                    self.errorMessage = item.error?.localizedDescription ?? "خطأ تقني في التشغيل"
                    self.isBuffering = false
                default:
                    break
                }
            }.store(in: &cancellables)
            
        // مراقبة المدة الزمنية بشكل دقيق لتفادي مشكلة الـ (00:00)
        item.publisher(for: \.duration)
            .receive(on: RunLoop.main)
            .sink { [weak self] dur in
                let secs = dur.seconds
                self?.duration = (secs.isNaN || secs.isInfinite) ? 0 : secs
            }.store(in: &cancellables)
        
        player.publisher(for: \.timeControlStatus)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                guard let self = self else { return }
                self.isPlaying = (status == .playing)
                if status == .playing {
                    self.isBuffering = false
                }
                if status == .waitingToPlayAtSpecifiedRate {
                    self.isBuffering = true
                }
            }.store(in: &cancellables)
        
        NotificationCenter.default.publisher(for: .AVPlayerItemPlaybackStalled, object: item)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.isBuffering = true
            }.store(in: &cancellables)
        
        // تحديث شريط الوقت بدون توقف
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        self.timeObserver = self.player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.currentTime = time.seconds
            }
        }
    }
    
    func togglePlay() {
        if self.player.timeControlStatus == .playing {
            self.player.pause()
        } else {
            self.player.play()
        }
    }
    
    func seek(by seconds: Double) {
        let targetTime = max(0, min(self.currentTime + seconds, self.duration > 0 ? self.duration : .infinity))
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        self.player.seek(to: target)
    }
    
    func seekTo(fraction: Double) {
        guard self.duration > 0 else { return } // لا تسحب الشريط إذا كان بث مباشر
        let targetTime = self.duration * fraction
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        
        self.player.seek(to: target) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.currentTime = targetTime
            }
        }
    }
    
    func cleanupAll() {
        if let observer = self.timeObserver {
            self.player.removeTimeObserver(observer)
            self.timeObserver = nil
        }
        self.cancellables.removeAll()
        self.player.pause()
        self.player.replaceCurrentItem(with: nil)
    }
    
    deinit {
        if let observer = self.timeObserver {
            self.player.removeTimeObserver(observer)
        }
    }
}

// MARK: - Video Layer
class PlayerUIView: UIView {
    var playerLayer: AVPlayerLayer {
        return layer as! AVPlayerLayer
    }
    override class var layerClass: AnyClass {
        return AVPlayerLayer.self
    }
}

struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let gravity: AVLayerVideoGravity

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.backgroundColor = .black
        view.playerLayer.player = player
        view.playerLayer.videoGravity = gravity
        return view
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.playerLayer.player = player
        uiView.playerLayer.videoGravity = gravity
    }
}

// MARK: - Main Player View
struct NativePlayerView: View {
    let channel: Channel
    
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = NativePlayerCoordinator()
    
    @State private var showControls: Bool = true
    @State private var videoGravity: AVLayerVideoGravity = .resizeAspect
    @State private var hideTimer: Timer?
    @State private var isResolving: Bool = false
    
    // Dialog States
    @State private var showSettingsDialog: Bool = false
    @State private var showServerDialog: Bool = false
    @State private var showAudioDialog: Bool = false
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            PlayerVideoLayer(player: viewModel.player, gravity: videoGravity)
                .ignoresSafeArea()
            
            // 1. الحل الجذري لمشكلة الأزرار: طبقة زجاجية تلتقط لمس الشاشة لإخفاء/إظهار المشغل وتكون "خلف" الأزرار
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture {
                    self.toggleControls()
                }
            
            // 2. مؤشر التحميل
            if viewModel.isBuffering && viewModel.errorMessage == nil && !isResolving {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(colorRed)
                    .scaleEffect(1.6)
                    .allowsHitTesting(false)
            }
            
            if isResolving {
                Color.black.opacity(0.6).ignoresSafeArea()
                VStack(spacing: 16) {
                    ProgressView().tint(colorRed).scaleEffect(1.6)
                }
            }
            
            // 3. رسالة الخطأ
            if let error = viewModel.errorMessage {
                Color.black.opacity(0.9).ignoresSafeArea()
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 64))
                        .foregroundColor(colorRed)
                    Text(error)
                        .foregroundColor(.white)
                        .font(.system(size: 16, weight: .bold))
                        .multilineTextAlignment(.center)
                        .padding(32)
                    
                    PlayerIconButton(type: .back, size: 36) {
                        dismiss()
                    }
                }
            }
            
            // 4. طبقة التحكم (الأزرار ستتفاعل الآن بكفاءة لأن إيماءة اللمس أصبحت خلفها)
            if showControls && viewModel.errorMessage == nil && !isResolving {
                controlsLayer
                    .transition(.opacity.animation(.easeInOut(duration: 0.2)))
            }
            
            // 5. النوافذ العائمة (تصميم أندرويد)
            if showSettingsDialog {
                APiXDialog(title: "الجودة", isPresented: $showSettingsDialog) {
                    ForEach(Array(viewModel.qualities.enumerated()), id: \.offset) { index, quality in
                        APiXDialogRow(title: quality.label, isSelected: index == viewModel.currentQualityIndex) {
                            self.viewModel.setQuality(index: index)
                            self.showSettingsDialog = false
                            self.resetTimer()
                        }
                    }
                }
            }
            
            if showServerDialog {
                APiXDialog(title: "اختر السيرفر", isPresented: $showServerDialog) {
                    ForEach(Array(viewModel.servers.enumerated()), id: \.offset) { index, server in
                        APiXDialogRow(title: server.name, isSelected: index == viewModel.currentServerIndex) {
                            self.viewModel.loadServer(index: index)
                            self.showServerDialog = false
                            self.resetTimer()
                        }
                    }
                }
            }
            
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        // قمنا بحذف الـ onTapGesture الخاطئ من هنا!
        .onAppear {
            self.forceLandscape()
            self.resetTimer()
        }
        .onDisappear {
            self.forcePortrait()
            self.viewModel.cleanupAll()
            self.hideTimer?.invalidate()
        }
        .task {
            await self.resolveAndPlay()
        }
    }
    
    // MARK: - Controls Layer
    private var controlsLayer: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 100)
                    .allowsHitTesting(false) // يسمح بمرور اللمس للخلفية لإخفاء المشغل
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 120)
                    .allowsHitTesting(false)
            }
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                HStack {
                    PlayerIconButton(type: .back, size: 36) {
                        self.viewModel.player.pause()
                        self.dismiss()
                    }
                    Spacer()
                    Text(viewModel.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .frame(maxWidth: 300, alignment: .trailing)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                
                Spacer()
                
                VStack(spacing: 4) {
                    HStack(spacing: 8) {
                        Text(formatTime(viewModel.currentTime))
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        
                        let progressValue = viewModel.duration > 0 ? (viewModel.currentTime / viewModel.duration) : 0
                        IOSProgressSlider(value: progressValue) { fraction in
                            if viewModel.duration > 0 {
                                self.viewModel.seekTo(fraction: fraction)
                            }
                            self.resetTimer()
                        }
                        .frame(height: 16)
                        
                        Text(formatTime(viewModel.duration))
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 16)
                    
                    HStack {
                        HStack(spacing: 16) {
                            PlayerIconButton(type: .rewind, size: 38) {
                                self.viewModel.seek(by: -10)
                                self.resetTimer()
                            }
                            PlayerIconButton(type: viewModel.isPlaying ? .pause : .play, size: 44) {
                                self.viewModel.togglePlay()
                                self.resetTimer()
                            }
                            PlayerIconButton(type: .forward, size: 38) {
                                self.viewModel.seek(by: 10)
                                self.resetTimer()
                            }
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 12) {
                            if !viewModel.audioSources.isEmpty {
                                PlayerImageButton(systemName: "waveform", size: 32) {
                                    self.showAudioDialog = true
                                    self.hideTimer?.invalidate()
                                }
                            }
                            
                            if viewModel.servers.count > 1 {
                                PlayerIconButton(type: .server, size: 32) {
                                    self.showServerDialog = true
                                    self.hideTimer?.invalidate()
                                }
                            }
                            
                            PlayerIconButton(type: .settings, size: 32) {
                                self.showSettingsDialog = true
                                self.hideTimer?.invalidate()
                            }
                            
                            PlayerIconButton(type: .resize, size: 32) {
                                let isAspect = videoGravity == .resizeAspect
                                self.videoGravity = isAspect ? .resizeAspectFill : .resizeAspect
                                self.resetTimer()
                            }
                            
                            PlayerIconButton(type: .pip, size: 32) {
                                // إعدادات الـ PiP
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
            }
        }
    }
    
    // MARK: - Handlers
    private func resolveAndPlay() async {
        let urlString = channel.playbackURL ?? ""
        if ApixStreamResolverIOS.isApixStream(urlString) {
            self.isResolving = true
            if let config = await ApixStreamResolverIOS.resolve(urlString) {
                self.viewModel.setupFromApix(config, fallbackTitle: channel.name)
            } else {
                self.viewModel.setup(channel: channel)
            }
            self.isResolving = false
        } else {
            self.viewModel.setup(channel: channel)
        }
    }
    
    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) {
            self.showControls.toggle()
        }
        if self.showControls {
            self.resetTimer()
        }
    }
    
    private func resetTimer() {
        self.hideTimer?.invalidate()
        self.hideTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in
            withAnimation(.easeInOut(duration: 0.3)) {
                self.showControls = false
            }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && !seconds.isNaN && seconds >= 0 else { return "00:00" }
        let h = Int(seconds) / 3600
        let m = (Int(seconds) % 3600) / 60
        let s = Int(seconds) % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        } else {
            return String(format: "%02d:%02d", m, s)
        }
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

// MARK: - UI Buttons & Scale Effect
struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 1.2 : 1.0)
            .animation(.easeOut(duration: 0.2), value: configuration.isPressed)
    }
}

struct PlayerIconButton: View {
    let type: PlayerIconType
    var size: CGFloat
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.clear).frame(width: size, height: size)
                PlayerIconPath(type: type)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: size * 0.65, height: size * 0.65)
            }
        }
        .buttonStyle(ScaleButtonStyle())
    }
}

struct PlayerImageButton: View {
    let systemName: String
    var size: CGFloat
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.clear).frame(width: size, height: size)
                Image(systemName: systemName)
                    .font(.system(size: size * 0.55, weight: .medium))
                    .foregroundColor(.white)
            }
        }
        .buttonStyle(ScaleButtonStyle())
    }
}

// MARK: - Slider (Matches Android Colors/Behavior)
struct IOSProgressSlider: View {
    let value: Double
    let onEnd: (Double) -> Void
    @State private var dragValue: Double? = nil
    
    var body: some View {
        GeometryReader { geometry in
            let displayValue = dragValue ?? value
            let maxWidth = geometry.size.width
            let sliderWidth = max(0, maxWidth * CGFloat(displayValue))
            let circleOffset = max(0, min(maxWidth - 12, sliderWidth - 6))
            
            ZStack(alignment: .leading) {
                // Inactive Track
                Capsule()
                    .fill(Color.white.opacity(0.26)) // 0x44FFFFFF
                    .frame(height: 3)
                // Active Track
                Capsule()
                    .fill(colorRed)
                    .frame(width: sliderWidth, height: 3)
                // Thumb
                Circle()
                    .fill(Color.white)
                    .frame(width: 12, height: 12)
                    .offset(x: circleOffset)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        self.dragValue = fraction // يضمن حركة المؤشر مع الإصبع
                    }
                    .onEnded { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        self.onEnd(fraction)
                        self.dragValue = nil
                    }
            )
        }
    }
}

// MARK: - Custom APiX Dialog (Exact Android Match)
struct APiXDialog<Content: View>: View {
    let title: String
    @Binding var isPresented: Bool
    @ViewBuilder let content: () -> Content
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture { isPresented = false }
            
            VStack(spacing: 0) {
                Text(title)
                    .foregroundColor(colorGold)
                    .font(.system(size: 16, weight: .bold))
                    .padding(16)
                
                Divider().background(colorDivider)
                
                ScrollView {
                    VStack(spacing: 4) {
                        content()
                    }
                    .padding(8)
                }
                
                Divider().background(colorDivider)
                
                Button(action: { isPresented = false }) {
                    Text("إغلاق")
                        .foregroundColor(colorGold)
                        .font(.system(size: 14, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(12)
                }
            }
            .background(colorBgDark)
            .cornerRadius(12)
            .frame(width: UIScreen.main.bounds.width > 500 ? 350 : UIScreen.main.bounds.width * 0.45)
            .frame(maxHeight: UIScreen.main.bounds.height * 0.85)
        }
        .transition(.opacity)
    }
}

struct APiXDialogRow: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .foregroundColor(isSelected ? colorGold : .white)
                    .font(.system(size: 13, weight: isSelected ? .bold : .regular))
                    .lineLimit(1)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(colorGold)
                        .font(.system(size: 18))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(isSelected ? colorRowSelected : Color.clear)
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Paths
struct PlayerIconPath: Shape {
    let type: PlayerIconType
    
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let scaleX = rect.width / 24.0
        let scaleY = rect.height / 24.0
        
        switch type {
        case .play:
            path.move(to: CGPoint(x: 8 * scaleX, y: 6 * scaleY))
            path.addLine(to: CGPoint(x: 8 * scaleX, y: 18 * scaleY))
            path.addLine(to: CGPoint(x: 18 * scaleX, y: 12 * scaleY))
            path.closeSubpath()
        case .pause:
            path.move(to: CGPoint(x: 6 * scaleX, y: 7 * scaleY))
            path.addArc(center: CGPoint(x: 8 * scaleX, y: 7 * scaleY), radius: 2 * scaleX, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            path.addLine(to: CGPoint(x: 10 * scaleX, y: 17 * scaleY))
            path.addArc(center: CGPoint(x: 8 * scaleX, y: 17 * scaleY), radius: 2 * scaleX, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
            path.closeSubpath()
            path.move(to: CGPoint(x: 14 * scaleX, y: 7 * scaleY))
            path.addArc(center: CGPoint(x: 16 * scaleX, y: 7 * scaleY), radius: 2 * scaleX, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            path.addLine(to: CGPoint(x: 18 * scaleX, y: 17 * scaleY))
            path.addArc(center: CGPoint(x: 16 * scaleX, y: 17 * scaleY), radius: 2 * scaleX, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
            path.closeSubpath()
        case .forward:
            path.move(to: CGPoint(x: 9 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 14 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 9 * scaleX, y: 17 * scaleY))
            path.move(to: CGPoint(x: 15 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 20 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 15 * scaleX, y: 17 * scaleY))
        case .rewind:
            path.move(to: CGPoint(x: 15 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 10 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 15 * scaleX, y: 17 * scaleY))
            path.move(to: CGPoint(x: 9 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 4 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 9 * scaleX, y: 17 * scaleY))
        case .back:
            path.move(to: CGPoint(x: 19 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 5 * scaleX, y: 12 * scaleY))
            path.move(to: CGPoint(x: 12 * scaleX, y: 19 * scaleY))
            path.addLine(to: CGPoint(x: 5 * scaleX, y: 12 * scaleY))
            path.addLine(to: CGPoint(x: 12 * scaleX, y: 5 * scaleY))
        case .resize:
            path.move(to: CGPoint(x: 15 * scaleX, y: 3 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 3 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 9 * scaleY))
            path.move(to: CGPoint(x: 9 * scaleX, y: 21 * scaleY))
            path.addLine(to: CGPoint(x: 3 * scaleX, y: 21 * scaleY))
            path.addLine(to: CGPoint(x: 3 * scaleX, y: 15 * scaleY))
            path.move(to: CGPoint(x: 21 * scaleX, y: 3 * scaleY))
            path.addLine(to: CGPoint(x: 14 * scaleX, y: 10 * scaleY))
            path.move(to: CGPoint(x: 3 * scaleX, y: 21 * scaleY))
            path.addLine(to: CGPoint(x: 10 * scaleX, y: 14 * scaleY))
        case .server:
            path.move(to: CGPoint(x: 2 * scaleX, y: 16.1 * scaleY))
            path.addArc(center: CGPoint(x: 2 * scaleX, y: 20 * scaleY), radius: 5 * scaleX, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
            path.move(to: CGPoint(x: 2 * scaleX, y: 12.05 * scaleY))
            path.addArc(center: CGPoint(x: 2 * scaleX, y: 20 * scaleY), radius: 9 * scaleX, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
            path.move(to: CGPoint(x: 2 * scaleX, y: 8 * scaleY))
            path.addArc(center: CGPoint(x: 2 * scaleX, y: 20 * scaleY), radius: 13 * scaleX, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
            path.move(to: CGPoint(x: 20 * scaleX, y: 4 * scaleY))
            path.addLine(to: CGPoint(x: 4 * scaleX, y: 4 * scaleY))
            path.move(to: CGPoint(x: 20 * scaleX, y: 4 * scaleY))
            path.addLine(to: CGPoint(x: 20 * scaleX, y: 20 * scaleY))
            path.addLine(to: CGPoint(x: 14 * scaleX, y: 20 * scaleY))
        case .pip:
            path.move(to: CGPoint(x: 9 * scaleX, y: 19 * scaleY))
            path.addLine(to: CGPoint(x: 5 * scaleX, y: 19 * scaleY))
            path.addLine(to: CGPoint(x: 3 * scaleX, y: 17 * scaleY))
            path.addLine(to: CGPoint(x: 3 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 5 * scaleX, y: 5 * scaleY))
            path.addLine(to: CGPoint(x: 19 * scaleX, y: 5 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 7 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 10 * scaleY))
            path.move(to: CGPoint(x: 13 * scaleX, y: 13 * scaleY))
            path.addLine(to: CGPoint(x: 19 * scaleX, y: 13 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 15 * scaleY))
            path.addLine(to: CGPoint(x: 21 * scaleX, y: 17 * scaleY))
            path.addLine(to: CGPoint(x: 19 * scaleX, y: 19 * scaleY))
            path.addLine(to: CGPoint(x: 13 * scaleX, y: 19 * scaleY))
            path.addLine(to: CGPoint(x: 11 * scaleX, y: 17 * scaleY))
            path.addLine(to: CGPoint(x: 11 * scaleX, y: 15 * scaleY))
            path.addLine(to: CGPoint(x: 13 * scaleX, y: 13 * scaleY))
            path.closeSubpath()
        case .settings:
            path.move(to: CGPoint(x: 12 * scaleX, y: 8 * scaleY))
            path.addCurve(to: CGPoint(x: 16 * scaleX, y: 12 * scaleY), control1: CGPoint(x: 14.2 * scaleX, y: 8 * scaleY), control2: CGPoint(x: 16 * scaleX, y: 9.8 * scaleY))
            path.addCurve(to: CGPoint(x: 12 * scaleX, y: 16 * scaleY), control1: CGPoint(x: 16 * scaleX, y: 14.2 * scaleY), control2: CGPoint(x: 14.2 * scaleX, y: 16 * scaleY))
            path.addCurve(to: CGPoint(x: 8 * scaleX, y: 12 * scaleY), control1: CGPoint(x: 9.8 * scaleX, y: 16 * scaleY), control2: CGPoint(x: 8 * scaleX, y: 14.2 * scaleY))
            path.addCurve(to: CGPoint(x: 12 * scaleX, y: 8 * scaleY), control1: CGPoint(x: 8 * scaleX, y: 9.8 * scaleY), control2: CGPoint(x: 9.8 * scaleX, y: 8 * scaleY))
            path.move(to: CGPoint(x: 19.4 * scaleX, y: 13 * scaleY))
            path.addLine(to: CGPoint(x: 21.3 * scaleX, y: 14.5 * scaleY))
            path.addLine(to: CGPoint(x: 19.4 * scaleX, y: 18.5 * scaleY))
            path.addLine(to: CGPoint(x: 16.8 * scaleX, y: 17.5 * scaleY))
            path.addLine(to: CGPoint(x: 14.5 * scaleX, y: 21 * scaleY))
            path.addLine(to: CGPoint(x: 9.5 * scaleX, y: 21 * scaleY))
            path.addLine(to: CGPoint(x: 7.2 * scaleX, y: 17.5 * scaleY))
            path.addLine(to: CGPoint(x: 4.6 * scaleX, y: 18.5 * scaleY))
            path.addLine(to: CGPoint(x: 2.7 * scaleX, y: 14.5 * scaleY))
            path.addLine(to: CGPoint(x: 4.6 * scaleX, y: 13 * scaleY))
            path.addLine(to: CGPoint(x: 4.6 * scaleX, y: 11 * scaleY))
            path.addLine(to: CGPoint(x: 2.7 * scaleX, y: 9.5 * scaleY))
            path.addLine(to: CGPoint(x: 4.6 * scaleX, y: 5.5 * scaleY))
            path.addLine(to: CGPoint(x: 7.2 * scaleX, y: 6.5 * scaleY))
            path.addLine(to: CGPoint(x: 9.5 * scaleX, y: 3 * scaleY))
            path.addLine(to: CGPoint(x: 14.5 * scaleX, y: 3 * scaleY))
            path.addLine(to: CGPoint(x: 16.8 * scaleX, y: 6.5 * scaleY))
            path.addLine(to: CGPoint(x: 19.4 * scaleX, y: 5.5 * scaleY))
            path.addLine(to: CGPoint(x: 21.3 * scaleX, y: 9.5 * scaleY))
            path.addLine(to: CGPoint(x: 19.4 * scaleX, y: 11 * scaleY))
            path.closeSubpath()
        }
        return path
    }
}

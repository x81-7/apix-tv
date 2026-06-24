import SwiftUI
import AVKit
import Combine

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
        PlayerQuality(label: "Auto", bitrate: 0),
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
            newServers.append(PlayerServer(name: "Server 1", url: primaryUrlString, headers: channel.effectiveHeaders, clearKey: channel.clearKeyCombined))
        }
        
        if let backupUrlString = channel.backupURL?.absoluteString, !backupUrlString.isEmpty {
            newServers.append(PlayerServer(name: "Backup", url: backupUrlString, headers: channel.effectiveHeaders, clearKey: channel.clearKeyCombined))
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
            PlayerServer(name: "Server 1", url: config.url, headers: config.headers, clearKey: config.clearKey)
        ]
        
        if let backupUrlString = config.backupUrl, !backupUrlString.isEmpty {
            newServers.append(PlayerServer(name: "Backup", url: backupUrlString, headers: config.headers, clearKey: config.clearKey))
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
        item.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                guard let self = self else { return }
                switch status {
                case .readyToPlay:
                    self.isBuffering = false
                    let itemDuration = item.duration.seconds
                    self.duration = itemDuration.isNaN || itemDuration.isInfinite ? 0 : itemDuration
                case .failed:
                    self.errorMessage = item.error?.localizedDescription ?? "Playback failed"
                    self.isBuffering = false
                default:
                    break
                }
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
        
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        self.timeObserver = self.player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if self.player.timeControlStatus == .playing {
                    self.currentTime = time.seconds
                }
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
        let targetTime = self.currentTime + seconds
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        self.player.seek(to: target)
    }
    
    func seekTo(fraction: Double) {
        let currentDuration = self.duration
        guard currentDuration > 0 else { return }
        let targetTime = currentDuration * fraction
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
struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let gravity: AVLayerVideoGravity

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = gravity
        view.layer.addSublayer(layer)
        context.coordinator.layer = layer
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.layer?.frame = uiView.bounds
        context.coordinator.layer?.videoGravity = gravity
    }

    func makeCoordinator() -> Coordinator {
        return Coordinator()
    }
    
    class Coordinator {
        var layer: AVPlayerLayer?
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
    
    @State private var showQualitySheet: Bool = false
    @State private var showServerSheet: Bool = false
    @State private var showAudioSheet: Bool = false
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            PlayerVideoLayer(player: viewModel.player, gravity: videoGravity)
                .ignoresSafeArea()
            
            if viewModel.isBuffering && viewModel.errorMessage == nil && !isResolving {
                Circle()
                    .stroke(Color(red: 229/255, green: 9/255, blue: 20/255), lineWidth: 3)
                    .frame(width: 52, height: 52)
                    .rotationEffect(.degrees(viewModel.isBuffering ? 360 : 0))
                    .animation(.linear(duration: 1).repeatForever(autoreverses: false), value: viewModel.isBuffering)
                    .opacity(viewModel.isBuffering ? 1 : 0)
                
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Color(red: 229/255, green: 9/255, blue: 20/255))
                    .scaleEffect(1.6)
            }
            
            if isResolving {
                Color.black.opacity(0.6).ignoresSafeArea()
                VStack(spacing: 16) {
                    ProgressView()
                        .tint(Color(red: 229/255, green: 9/255, blue: 20/255))
                        .scaleEffect(1.4)
                    Text("Loading Stream...")
                        .foregroundStyle(.white)
                        .font(.system(size: 14, weight: .medium))
                }
            }
            
            if let error = viewModel.errorMessage {
                Color.black.opacity(0.85).ignoresSafeArea()
                VStack(spacing: 20) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 52))
                        .foregroundColor(.red)
                    Text(error)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Button(action: {
                        dismiss()
                    }) {
                        Text("Close")
                            .padding(.horizontal, 30)
                            .padding(.vertical, 12)
                            .background(Color(red: 229/255, green: 9/255, blue: 20/255))
                            .cornerRadius(12)
                            .foregroundColor(.white)
                            .fontWeight(.bold)
                    }
                }
            }
            
            if showControls && viewModel.errorMessage == nil && !isResolving {
                controlsLayer
                    .transition(.opacity.animation(.easeInOut(duration: 0.2)))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            self.toggleControls()
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
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
        .sheet(isPresented: $showQualitySheet) {
            qualitySheetContent
        }
        .sheet(isPresented: $showServerSheet) {
            serverSheetContent
        }
        .sheet(isPresented: $showAudioSheet) {
            audioSheetContent
        }
    }
    
    private var controlsLayer: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.75), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 110)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 140)
            }
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                HStack {
                    CircleIconButton(iconType: .back, size: 36) {
                        self.viewModel.player.pause()
                        self.dismiss()
                    }
                    Spacer()
                    Text(viewModel.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Spacer()
                    Spacer().frame(width: 36)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                
                Spacer()
                
                HStack(spacing: 52) {
                    CircleIconButton(iconType: .rewind, size: 42) {
                        self.viewModel.seek(by: -10)
                        self.resetTimer()
                    }
                    CircleIconButton(iconType: viewModel.isPlaying ? .pause : .play, size: 52, isLarge: true) {
                        self.viewModel.togglePlay()
                        self.resetTimer()
                    }
                    CircleIconButton(iconType: .forward, size: 42) {
                        self.viewModel.seek(by: 10)
                        self.resetTimer()
                    }
                }
                
                Spacer()
                
                VStack(spacing: 6) {
                    HStack(spacing: 10) {
                        Text(formatTime(viewModel.currentTime))
                            .font(.system(size: 13, weight: .medium, design: .monospaced))
                            .foregroundColor(.white)
                        
                        let progressValue = viewModel.duration > 0 ? (viewModel.currentTime / viewModel.duration) : 0
                        IOSProgressSlider(value: progressValue) { fraction in
                            self.viewModel.seekTo(fraction: fraction)
                            self.resetTimer()
                        }
                        
                        Text(formatTime(viewModel.duration))
                            .font(.system(size: 13, weight: .medium, design: .monospaced))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    .padding(.horizontal, 20)
                    
                    HStack {
                        Spacer()
                        
                        if !viewModel.audioSources.isEmpty {
                            SmallIconBtn(systemName: "music.note") {
                                self.showAudioSheet = true
                            }
                        }
                        
                        if viewModel.servers.count > 1 {
                            SmallIconBtn(systemName: "server.rack") {
                                self.showServerSheet = true
                            }
                        }
                        
                        SmallIconBtn(systemName: "slider.horizontal.3") {
                            self.showQualitySheet = true
                        }
                        
                        let isAspect = videoGravity == .resizeAspect
                        SmallIconBtn(systemName: isAspect ? "arrow.up.left.and.arrow.down.right" : "arrow.down.right.and.arrow.up.left") {
                            self.videoGravity = isAspect ? .resizeAspectFill : .resizeAspect
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 18)
                }
            }
        }
    }
    
    private var qualitySheetContent: some View {
        IOSPlayerSheet(title: "Quality") {
            ForEach(Array(viewModel.qualities.enumerated()), id: \.offset) { index, quality in
                IOSSheetRow(title: quality.label, isSelected: index == viewModel.currentQualityIndex) {
                    self.viewModel.setQuality(index: index)
                    self.showQualitySheet = false
                }
            }
        }
    }

    private var serverSheetContent: some View {
        IOSPlayerSheet(title: "Servers") {
            ForEach(Array(viewModel.servers.enumerated()), id: \.offset) { index, server in
                IOSSheetRow(title: server.name, isSelected: index == viewModel.currentServerIndex) {
                    self.viewModel.loadServer(index: index)
                    self.showServerSheet = false
                }
            }
        }
    }

    private var audioSheetContent: some View {
        IOSPlayerSheet(title: "Audio Sources") {
            ForEach(viewModel.audioSources) { source in
                IOSSheetRow(title: source.name, isSelected: false) {
                    self.viewModel.load(url: source.url, headers: [:], clearKey: nil)
                    self.showAudioSheet = false
                }
            }
        }
    }
    
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
        self.hideTimer = Timer.scheduledTimer(withTimeInterval: 4, repeats: false) { _ in
            withAnimation(.easeInOut(duration: 0.3)) {
                self.showControls = false
            }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds > 0 else { return "00:00" }
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

// MARK: - UI Components
struct CircleIconButton: View {
    let iconType: PlayerIconType
    var size: CGFloat = 44
    var isLarge: Bool = false
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(isLarge ? 0.4 : 0.2), lineWidth: isLarge ? 2 : 1)
                    .background(Circle().fill(Color.white.opacity(0.12)))
                    .frame(width: size, height: size)
                
                PlayerIconPath(type: iconType)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: isLarge ? 2 : 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: size * (isLarge ? 0.55 : 0.5), height: size * (isLarge ? 0.55 : 0.5))
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

struct SmallIconBtn: View {
    let systemName: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 19, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 36, height: 36)
        }
        .buttonStyle(.plain)
    }
}

struct IOSProgressSlider: View {
    let value: Double
    let onEnd: (Double) -> Void
    @State private var dragValue: Double? = nil
    
    var body: some View {
        GeometryReader { geometry in
            let displayValue = dragValue ?? value
            let maxWidth = geometry.size.width
            let sliderWidth = max(0, maxWidth * CGFloat(displayValue))
            let circleOffset = max(0, min(maxWidth - 14, sliderWidth - 7))
            
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.white.opacity(0.25))
                    .frame(height: 4)
                Capsule()
                    .fill(Color(red: 229/255, green: 9/255, blue: 20/255))
                    .frame(width: sliderWidth, height: 4)
                Circle()
                    .fill(Color.white)
                    .frame(width: 14, height: 14)
                    .offset(x: circleOffset)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        self.dragValue = fraction
                    }
                    .onEnded { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        self.onEnd(fraction)
                        self.dragValue = nil
                    }
            )
        }
        .frame(height: 20)
    }
}

struct IOSPlayerSheet<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            List {
                content
            }
            .listStyle(.plain)
            .background(Color(red: 17/255, green: 17/255, blue: 17/255))
            .scrollContentBackground(.hidden)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") {
                        dismiss()
                    }
                    .foregroundColor(Color(red: 229/255, green: 9/255, blue: 20/255))
                }
            }
        }
        .presentationDetents([.medium])
        .preferredColorScheme(.dark)
    }
}

struct IOSSheetRow: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .foregroundColor(isSelected ? Color(red: 229/255, green: 9/255, blue: 20/255) : .white)
                    .fontWeight(isSelected ? .bold : .regular)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(Color(red: 229/255, green: 9/255, blue: 20/255))
                }
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .listRowBackground(Color(red: 26/255, green: 26/255, blue: 26/255))
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
            path.move(to: CGPoint(x: 9 * scaleX, y: 6 * scaleY))
            path.addLine(to: CGPoint(x: 9 * scaleX, y: 18 * scaleY))
            path.addLine(to: CGPoint(x: 18 * scaleX, y: 12 * scaleY))
            path.closeSubpath()
        case .pause:
            path.move(to: CGPoint(x: 8 * scaleX, y: 6 * scaleY))
            path.addLine(to: CGPoint(x: 8 * scaleX, y: 18 * scaleY))
            path.move(to: CGPoint(x: 16 * scaleX, y: 6 * scaleY))
            path.addLine(to: CGPoint(x: 16 * scaleX, y: 18 * scaleY))
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
        }
        return path
    }
}

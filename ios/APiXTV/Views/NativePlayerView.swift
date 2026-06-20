import SwiftUI
import AVKit
import Combine

// MARK: - Coordinator
@MainActor
final class NativePlayerCoordinator: ObservableObject {

    let player = AVPlayer()
    private var timeObserver: Any?
    private var statusObserver: AnyCancellable?
    private var itemObserver: AnyCancellable?

    @Published var isPlaying      = false
    @Published var isBuffering     = true
    @Published var currentTime: Double = 0
    @Published var duration: Double    = 0
    @Published var errorMessage: String?
    @Published var title = ""
    @Published var servers: [NamedServer] = []
    @Published var currentServerIndex = 0
    @Published var qualities: [QualityLevel] = [
        QualityLevel(label: "تلقائي", bitrate: 0),
        QualityLevel(label: "عالي",   bitrate: 5_000_000),
        QualityLevel(label: "متوسط",  bitrate: 2_000_000),
        QualityLevel(label: "منخفض",  bitrate: 800_000)
    ]
    @Published var selectedQualityIndex = 0

    struct NamedServer: Identifiable {
        let id = UUID()
        let name: String
        let url: String
        var headers: [String: String] = [:]
        var clearKey: String?
    }

    struct QualityLevel: Identifiable {
        let id = UUID()
        let label: String
        let bitrate: Double
    }

    func setup(channel: Channel) {
        title = channel.name
        buildServers(from: channel)
        loadServer(at: 0)
    }

    func setupFromApix(_ cfg: ApixResolvedConfig, fallbackTitle: String) {
        title = cfg.title ?? fallbackTitle
        var svrs: [NamedServer] = []
        svrs.append(NamedServer(name: "سيرفر 1", url: cfg.url,
                                headers: cfg.headers, clearKey: cfg.clearKey))
        if let bk = cfg.backupUrl {
            svrs.append(NamedServer(name: "احتياطي", url: bk,
                                    headers: cfg.headers, clearKey: cfg.clearKey))
        }
        servers = svrs
        loadServer(at: 0)
    }

    private func buildServers(from channel: Channel) {
        var svrs: [NamedServer] = []
        if let primary = channel.playbackURL?.absoluteString {
            svrs.append(NamedServer(
                name: "سيرفر 1", url: primary,
                headers: channel.effectiveHeaders,
                clearKey: channel.clearKeyCombined
            ))
        }
        if let backup = channel.backupURL?.absoluteString {
            svrs.append(NamedServer(
                name: "احتياطي", url: backup,
                headers: channel.effectiveHeaders,
                clearKey: channel.clearKeyCombined
            ))
        }
        if let extras = channel.iosStream?.customHeaders {
            for (i, _) in extras.enumerated() where i < svrs.count {
                svrs[i].headers.merge(extras) { _, new in new }
            }
        }
        servers = svrs
    }

    func loadServer(at index: Int) {
        guard servers.indices.contains(index) else { return }
        currentServerIndex = index
        let svr = servers[index]
        loadURL(svr.url, headers: svr.headers, clearKey: svr.clearKey)
    }

    private func loadURL(_ urlString: String, headers: [String: String], clearKey: String?) {
        removeObservers()
        errorMessage = nil
        isBuffering  = true
        currentTime  = 0
        duration     = 0

        guard let url = URL(string: urlString) else {
            errorMessage = "رابط غير صالح"; return
        }
        var opts: [String: Any] = [:]
        if !headers.isEmpty { opts["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        let asset    = AVURLAsset(url: url, options: opts.isEmpty ? nil : opts)
        let item     = AVPlayerItem(asset: asset)

        if let ck = clearKey, ck.contains(":") {
            let parts = ck.components(separatedBy: ":")
            if parts.count == 2 {
                let resolver = ClearKeyResolver(keyId: parts[0], key: parts[1])
                item.contentKeySessionDelegate = resolver
            }
        }

        player.replaceCurrentItem(with: item)
        attachObservers(item: item)
        player.play()
        isPlaying = true
    }

    func setQuality(_ index: Int) {
        selectedQualityIndex = index
        player.currentItem?.preferredPeakBitRate = qualities[index].bitrate
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
            self?.currentTime = time.seconds
        }

        itemObserver = item.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                switch status {
                case .readyToPlay: self?.isBuffering = false
                case .failed:
                    self?.errorMessage = item.error?.localizedDescription ?? "فشل التشغيل"
                    self?.isBuffering  = false
                default: break
                }
            }

        statusObserver = item.publisher(for: \.duration)
            .receive(on: RunLoop.main)
            .sink { [weak self] dur in
                if dur.isNumeric { self?.duration = dur.seconds }
            }

        NotificationCenter.default.addObserver(
            forName: AVPlayerItem.playbackStalledNotification,
            object: item, queue: .main
        ) { [weak self] _ in self?.isBuffering = true }

        NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification,
            object: item, queue: .main
        ) { [weak self] _ in self?.isPlaying = false }
    }

    private func removeObservers() {
        if let obs = timeObserver { player.removeTimeObserver(obs); timeObserver = nil }
        itemObserver  = nil
        statusObserver = nil
        NotificationCenter.default.removeObserver(self)
    }

    deinit { removeObservers() }
}

// MARK: - ClearKey stub
final class ClearKeyResolver: NSObject, AVContentKeySessionDelegate {
    let keyId: String; let key: String
    init(keyId: String, key: String) { self.keyId = keyId; self.key = key }
    func contentKeySession(_ session: AVContentKeySession,
                           didProvide request: AVContentKeyRequest) {
        guard let keyData = Data(hexString: key) else { return }
        let response = request.makeStreamingContentKeyRequestData(forApp: Data(),
                                                                   contentIdentifier: Data())
        try? request.processContentKeyResponse(AVContentKeyResponse(fairPlayStreamingKeyResponseData: keyData))
    }
}

private extension Data {
    init?(hexString: String) {
        let hex = hexString.replacingOccurrences(of: "[^0-9a-fA-F]", with: "", options: .regularExpression)
        guard hex.count % 2 == 0 else { return nil }
        var data = Data(capacity: hex.count / 2)
        var idx = hex.startIndex
        while idx < hex.endIndex {
            let next = hex.index(idx, offsetBy: 2)
            guard let byte = UInt8(hex[idx..<next], radix: 16) else { return nil }
            data.append(byte); idx = next
        }
        self = data
    }
}

// MARK: - Video Layer
struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        view.layer.addSublayer(layer)
        context.coordinator.playerLayer = layer
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.playerLayer?.frame = uiView.bounds
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    class Coordinator {
        var playerLayer: AVPlayerLayer?
        func updateFrame(_ bounds: CGRect) { playerLayer?.frame = bounds }
    }
}

// MARK: - Main Player View
struct NativePlayerView: View {
    let channel: Channel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var coordinator = NativePlayerCoordinator()

    @State private var showControls      = true
    @State private var showQualitySheet  = false
    @State private var showServerSheet   = false
    @State private var controlsTimer: Timer?
    @State private var resizeMode: AVLayerVideoGravity = .resizeAspect
    @State private var isApixResolving   = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            PlayerVideoLayer(player: coordinator.player)
                .ignoresSafeArea()

            if coordinator.isBuffering && coordinator.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(AppTheme.gold)
                    .scaleEffect(1.5)
            }

            if let err = coordinator.errorMessage {
                ErrorOverlay(message: err) { dismiss() }
            }

            if showControls && coordinator.errorMessage == nil {
                controlsOverlay
            }

            if isApixResolving {
                Color.black.opacity(0.6).ignoresSafeArea()
                VStack(spacing: 16) {
                    ProgressView().tint(AppTheme.gold).scaleEffect(1.4)
                    Text("جاري تحميل الرابط...").foregroundStyle(.white)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { toggleControlsVisibility() }
        .statusBarHidden(true)
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.hidden)
        .task {
            if ApixStreamResolverIOS.isApixStream(
                channel.playbackURL?.absoluteString ?? "") {
                isApixResolving = true
                if let cfg = await ApixStreamResolverIOS.resolve(
                    channel.playbackURL!.absoluteString) {
                    coordinator.setupFromApix(cfg, fallbackTitle: channel.name)
                } else {
                    coordinator.setup(channel: channel)
                }
                isApixResolving = false
            } else {
                coordinator.setup(channel: channel)
            }
        }
        .sheet(isPresented: $showQualitySheet) { qualitySheet }
        .sheet(isPresented: $showServerSheet)  { serverSheet  }
    }

    // MARK: Controls Overlay
    private var controlsOverlay: some View {
        ZStack {
            LinearGradient(
                colors: [.black.opacity(0.7), .clear, .clear, .black.opacity(0.75)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            // Top bar
            VStack {
                HStack {
                    Button {
                        coordinator.player.pause()
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(Circle().fill(.black.opacity(0.4)))
                    }

                    Spacer()

                    Text(coordinator.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .padding(.horizontal, 8)

                    Spacer()
                    Spacer().frame(width: 44)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                Spacer()

                // Center controls
                HStack(spacing: 48) {
                    PlayerIconButton(systemName: "gobackward.10") {
                        coordinator.seek(by: -10)
                        resetControlsTimer()
                    }
                    PlayerIconButton(
                        systemName: coordinator.isPlaying ? "pause.fill" : "play.fill",
                        size: 52
                    ) {
                        coordinator.togglePlay()
                        resetControlsTimer()
                    }
                    PlayerIconButton(systemName: "goforward.10") {
                        coordinator.seek(by: 10)
                        resetControlsTimer()
                    }
                }

                Spacer()

                // Bottom bar
                VStack(spacing: 6) {
                    ProgressSlider(
                        value: coordinator.duration > 0
                            ? coordinator.currentTime / coordinator.duration : 0,
                        onChanged: { coordinator.seek(to: $0) }
                    )
                    .padding(.horizontal, 16)

                    HStack {
                        Text(formatTime(coordinator.currentTime))
                            .font(.system(size: 12, weight: .medium, design: .monospaced))
                            .foregroundStyle(.white)
                        Spacer()
                        Text(formatTime(coordinator.duration))
                            .font(.system(size: 12, weight: .medium, design: .monospaced))
                            .foregroundStyle(AppTheme.muted)
                    }
                    .padding(.horizontal, 18)

                    HStack(spacing: 20) {
                        Spacer()

                        if coordinator.servers.count > 1 {
                            BottomBarButton(systemName: "server.rack") {
                                showServerSheet = true
                            }
                        }

                        BottomBarButton(systemName: "slider.horizontal.3") {
                            showQualitySheet = true
                        }

                        BottomBarButton(
                            systemName: resizeMode == .resizeAspect
                                ? "arrow.up.left.and.arrow.down.right"
                                : "arrow.down.right.and.arrow.up.left"
                        ) {
                            resizeMode = resizeMode == .resizeAspect
                                ? .resizeAspectFill : .resizeAspect
                        }

                        if UIDevice.current.supportsMultipleScenes {
                            BottomBarButton(systemName: "pip.enter") {
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
        }
    }

    // MARK: Quality Sheet
    private var qualitySheet: some View {
        PlayerSheet(title: "الجودة") {
            ForEach(Array(coordinator.qualities.enumerated()), id: \.offset) { index, q in
                PlayerSheetRow(
                    title: q.label,
                    isSelected: coordinator.selectedQualityIndex == index
                ) {
                    coordinator.setQuality(index)
                    showQualitySheet = false
                }
            }
        }
    }

    // MARK: Server Sheet
    private var serverSheet: some View {
        PlayerSheet(title: "السيرفرات") {
            ForEach(Array(coordinator.servers.enumerated()), id: \.offset) { index, svr in
                PlayerSheetRow(
                    title: svr.name,
                    isSelected: coordinator.currentServerIndex == index
                ) {
                    coordinator.loadServer(at: index)
                    showServerSheet = false
                }
            }
        }
    }

    // MARK: Helpers
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
        return h > 0
            ? String(format: "%d:%02d:%02d", h, m, s)
            : String(format: "%02d:%02d", m, s)
    }
}

// MARK: - Sub-views

struct PlayerIconButton: View {
    let systemName: String
    var size: CGFloat = 38
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size * 0.6, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Circle().fill(.white.opacity(0.12)))
        }
    }
}

struct BottomBarButton: View {
    let systemName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
        }
    }
}

struct ProgressSlider: View {
    let value: Double
    let onChanged: (Double) -> Void
    @State private var dragging = false
    @State private var dragValue: Double = 0

    var body: some View {
        GeometryReader { geo in
            let display = dragging ? dragValue : value
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.25)).frame(height: 4)
                Capsule()
                    .fill(AppTheme.gold)
                    .frame(width: geo.size.width * display, height: 4)
                Circle()
                    .fill(.white)
                    .frame(width: 14, height: 14)
                    .offset(x: geo.size.width * display - 7)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        dragging = true
                        dragValue = max(0, min(1, v.location.x / geo.size.width))
                    }
                    .onEnded { v in
                        let fraction = max(0, min(1, v.location.x / geo.size.width))
                        onChanged(fraction)
                        dragging  = false
                    }
            )
        }
        .frame(height: 20)
    }
}

struct PlayerSheet<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List { content }
                .listStyle(.plain)
                .background(AppTheme.background)
                .scrollContentBackground(.hidden)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("إغلاق") { dismiss() }.foregroundStyle(AppTheme.gold)
                    }
                }
        }
        .background(AppTheme.background)
        .preferredColorScheme(.dark)
        .presentationDetents([.medium])
    }
}

struct PlayerSheetRow: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .foregroundStyle(isSelected ? AppTheme.gold : .white)
                    .fontWeight(isSelected ? .bold : .regular)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(AppTheme.gold)
                }
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .listRowBackground(AppTheme.surface)
    }
}

struct ErrorOverlay: View {
    let message: String
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 52))
                .foregroundStyle(Color.red)
            Text(message)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("رجوع", action: onBack)
                .padding(.horizontal, 32)
                .padding(.vertical, 12)
                .background(AppTheme.gold)
                .foregroundStyle(.black)
                .fontWeight(.bold)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
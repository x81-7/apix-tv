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

// MARK: - Coordinator
@MainActor
final class NativePlayerCoordinator: ObservableObject {

    let player = AVPlayer()
    private var timeObs: Any?
    private var bag = Set<AnyCancellable>()

    @Published var isPlaying  = false
    @Published var isBuffering = true
    @Published var currentTime: Double = 0
    @Published var duration:    Double = 0
    @Published var errorMessage: String?
    @Published var title = ""

    @Published var servers:    [PlayerServer]  = []
    @Published var currentSrv = 0
    @Published var qualities:  [PlayerQuality] = [
        PlayerQuality(label: "تلقائي",   bitrate: 0),
        PlayerQuality(label: "4K",       bitrate: 20_000_000),
        PlayerQuality(label: "1080p",    bitrate: 8_000_000),
        PlayerQuality(label: "720p",     bitrate: 4_000_000),
        PlayerQuality(label: "480p",     bitrate: 1_500_000),
        PlayerQuality(label: "360p",     bitrate: 800_000)
    ]
    @Published var currentQuality = 0
    @Published var audioSources: [PlayerAudioSource] = []

    init() {
        try? AVAudioSession.sharedInstance()
            .setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func setup(channel: Channel) {
        title = channel.name
        var svrs: [PlayerServer] = []
        if let primary = channel.playbackURL?.absoluteString {
            svrs.append(PlayerServer(name: "سيرفر 1", url: primary,
                                     headers: channel.effectiveHeaders,
                                     clearKey: channel.clearKeyCombined))
        }
        if let backup = channel.backupURL?.absoluteString {
            svrs.append(PlayerServer(name: "احتياطي", url: backup,
                                     headers: channel.effectiveHeaders,
                                     clearKey: channel.clearKeyCombined))
        }
        if let extras = channel.iosStream?.servers {
            for (i, s) in extras.enumerated() {
                if let u = s.url {
                    svrs.append(PlayerServer(name: s.name ?? "سيرفر \(i+2)",
                                             url: u, headers: [:], clearKey: nil))
                }
            }
        }
        if let extraAudio = channel.iosStream?.audioSources {
            audioSources = extraAudio.compactMap {
                guard let n = $0.name, let u = $0.url else { return nil }
                return PlayerAudioSource(name: n, url: u)
            }
        }
        servers = svrs
        loadServer(0)
    }

    func setupFromApix(_ cfg: ApixResolvedConfig, fallbackTitle: String) {
        title = cfg.title ?? fallbackTitle
        var svrs: [PlayerServer] = [
            PlayerServer(name: "سيرفر 1", url: cfg.url,
                         headers: cfg.headers, clearKey: cfg.clearKey)
        ]
        if let bk = cfg.backupUrl {
            svrs.append(PlayerServer(name: "احتياطي", url: bk,
                                     headers: cfg.headers, clearKey: cfg.clearKey))
        }
        servers = svrs
        loadServer(0)
    }

    func loadServer(_ idx: Int) {
        guard servers.indices.contains(idx) else { return }
        currentSrv = idx
        let s = servers[idx]
        load(url: s.url, headers: s.headers, clearKey: s.clearKey)
    }

    func setQuality(_ idx: Int) {
        currentQuality = idx
        player.currentItem?.preferredPeakBitRate = qualities[idx].bitrate
    }

    private func load(url: String, headers: [String: String], clearKey: String?) {
        cleanAll()
        errorMessage = nil; isBuffering = true; currentTime = 0; duration = 0
        guard let u = URL(string: url) else { errorMessage = "رابط غير صالح"; return }
        var opts: [String: Any] = [:]
        if !headers.isEmpty { opts["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        let item = AVPlayerItem(asset: AVURLAsset(url: u, options: opts.isEmpty ? nil : opts))
        player.replaceCurrentItem(with: item)
        observe(item)
        player.play()
    }

    private func observe(_ item: AVPlayerItem) {
        item.publisher(for: \.status).receive(on: RunLoop.main)
            .sink { [weak self] s in
                switch s {
                case .readyToPlay:
                    self?.isBuffering = false
                    let d = item.duration.seconds
                    self?.duration = d.isNaN || d.isInfinite ? 0 : d
                case .failed:
                    self?.errorMessage = item.error?.localizedDescription ?? "فشل التشغيل"
                    self?.isBuffering  = false
                default: break
                }
            }.store(in: &bag)

        player.publisher(for: \.timeControlStatus).receive(on: RunLoop.main)
            .sink { [weak self] s in
                self?.isPlaying  = s == .playing
                if s == .playing { self?.isBuffering = false }
                if s == .waitingToPlayAtSpecifiedRate { self?.isBuffering = true }
            }.store(in: &bag)

        timeObs = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] t in
            if self?.player.timeControlStatus == .playing {
                self?.currentTime = t.seconds
            }
        }
    }

    func togglePlay() {
        player.timeControlStatus == .playing ? player.pause() : player.play()
    }

    func seek(by secs: Double) {
        let t = CMTime(seconds: currentTime + secs, preferredTimescale: 600)
        player.seek(to: t)
    }

    func seekTo(fraction: Double) {
        guard duration > 0 else { return }
        let t = CMTime(seconds: duration * fraction, preferredTimescale: 600)
        player.seek(to: t) { [weak self] _ in
            self?.currentTime = (self?.duration ?? 0) * fraction
        }
    }

    func cleanAll() {
        if let o = timeObs { player.removeTimeObserver(o); timeObs = nil }
        bag.removeAll(); player.pause()
        player.replaceCurrentItem(with: nil)
    }

    deinit { if let o = timeObs { player.removeTimeObserver(o) } }
}

// MARK: - Video Layer
struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let gravity: AVLayerVideoGravity

    func makeUIView(context: Context) -> UIView {
        let v = UIView(); v.backgroundColor = .black
        let l = AVPlayerLayer(player: player)
        l.videoGravity = gravity
        v.layer.addSublayer(l)
        context.coordinator.layer = l
        return v
    }

    func updateUIView(_ v: UIView, context: Context) {
        context.coordinator.layer?.frame       = v.bounds
        context.coordinator.layer?.videoGravity = gravity
    }

    func makeCoordinator() -> C { C() }
    class C { var layer: AVPlayerLayer? }
}

// MARK: - NativePlayerView
struct NativePlayerView: View {
    let channel: Channel

    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm = NativePlayerCoordinator()

    @State private var showCtrl  = true
    @State private var gravity: AVLayerVideoGravity = .resizeAspect
    @State private var timer: Timer?

    @State private var showQuality = false
    @State private var showServers = false
    @State private var showAudio   = false
    @State private var isResolving = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            PlayerVideoLayer(player: vm.player, gravity: gravity)
                .ignoresSafeArea()

            // Buffering
            if vm.isBuffering && vm.errorMessage == nil && !isResolving {
                Circle().stroke(AppTheme.gold, lineWidth: 3)
                    .frame(width: 52, height: 52)
                    .rotationEffect(.degrees(vm.isBuffering ? 360 : 0))
                    .animation(.linear(duration: 1).repeatForever(autoreverses: false),
                               value: vm.isBuffering)
                    .opacity(vm.isBuffering ? 1 : 0)
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(AppTheme.gold)
                    .scaleEffect(1.6)
            }

            // Resolving apix
            if isResolving {
                Color.black.opacity(0.55).ignoresSafeArea()
                VStack(spacing: 14) {
                    ProgressView().tint(AppTheme.gold).scaleEffect(1.4)
                    Text("جاري تحميل الرابط...")
                        .foregroundStyle(.white)
                        .font(.system(size: 14, weight: .medium))
                }
            }

            // Error
            if let err = vm.errorMessage {
                Color.black.opacity(0.85).ignoresSafeArea()
                VStack(spacing: 20) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 52)).foregroundStyle(.red)
                    Text(err).foregroundStyle(.white)
                        .multilineTextAlignment(.center).padding(.horizontal, 32)
                    Button("رجوع") { dismiss() }
                        .padding(.horizontal, 32).padding(.vertical, 12)
                        .background(AppTheme.gold).foregroundStyle(.black)
                        .fontWeight(.bold)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }

            // Controls
            if showCtrl && vm.errorMessage == nil && !isResolving {
                controlsLayer
                    .transition(.opacity.animation(.easeInOut(duration: 0.2)))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { toggleCtrl() }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear { forceLandscape(); resetTimer() }
        .onDisappear { forcePortrait(); vm.cleanAll(); timer?.invalidate() }
        .task { await resolveAndPlay() }
        .sheet(isPresented: $showQuality) { qualitySheet }
        .sheet(isPresented: $showServers)  { serverSheet  }
        .sheet(isPresented: $showAudio)    { audioSheet   }
    }

    // ── Controls Layer ─────────────────────────────────────────────────
    private var controlsLayer: some View {
        ZStack {
            // الخلفية
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.75), .clear],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 110)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.85)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 140)
            }
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    CircleIconBtn(icon: "chevron.left", size: 36) {
                        vm.player.pause(); dismiss()
                    }
                    Spacer()
                    Text(vm.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Spacer()
                    Spacer().frame(width: 36)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)

                Spacer()

                // Center controls — مطابق للأندرويد
                HStack(spacing: 52) {
                    CircleIconBtn(icon: "gobackward.10", size: 42) {
                        vm.seek(by: -10); resetTimer()
                    }
                    CircleIconBtn(icon: vm.isPlaying ? "pause.fill" : "play.fill",
                                  size: 52, large: true) {
                        vm.togglePlay(); resetTimer()
                    }
                    CircleIconBtn(icon: "goforward.10", size: 42) {
                        vm.seek(by: 10); resetTimer()
                    }
                }

                Spacer()

                // Bottom area
                VStack(spacing: 6) {
                    // Slider + times
                    HStack(spacing: 10) {
                        Text(fmtTime(vm.currentTime))
                            .font(.system(size: 13, weight: .medium, design: .monospaced))
                            .foregroundStyle(.white)
                        IOSProgressSlider(
                            value: vm.duration > 0 ? vm.currentTime / vm.duration : 0
                        ) { vm.seekTo(fraction: $0); resetTimer() }
                        Text(fmtTime(vm.duration))
                            .font(.system(size: 13, weight: .medium, design: .monospaced))
                            .foregroundStyle(.white.opacity(0.7))
                    }
                    .padding(.horizontal, 20)

                    // Bottom buttons row
                    HStack {
                        Spacer()

                        if !vm.audioSources.isEmpty {
                            SmallIconBtn(icon: "music.note") { showAudio = true }
                        }
                        if vm.servers.count > 1 {
                            SmallIconBtn(icon: "server.rack") { showServers = true }
                        }
                        SmallIconBtn(icon: "slider.horizontal.3") { showQuality = true }
                        SmallIconBtn(
                            icon: gravity == .resizeAspect
                                ? "arrow.up.left.and.arrow.down.right"
                                : "arrow.down.right.and.arrow.up.left"
                        ) {
                            gravity = gravity == .resizeAspect
                                ? .resizeAspectFill : .resizeAspect
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 18)
                }
            }
        }
    }

    // ── Sheets ──────────────────────────────────────────────────────────
    private var qualitySheet: some View {
        IOSPlayerSheet(title: "الجودة") {
            ForEach(Array(vm.qualities.enumerated()), id: \.offset) { i, q in
                IOSSheetRow(title: q.label, selected: i == vm.currentQuality) {
                    vm.setQuality(i); showQuality = false
                }
            }
        }
    }

    private var serverSheet: some View {
        IOSPlayerSheet(title: "السيرفرات") {
            ForEach(Array(vm.servers.enumerated()), id: \.offset) { i, s in
                IOSSheetRow(title: s.name, selected: i == vm.currentSrv) {
                    vm.loadServer(i); showServers = false
                }
            }
        }
    }

    private var audioSheet: some View {
        IOSPlayerSheet(title: "مصادر الصوت") {
            ForEach(vm.audioSources) { src in
                IOSSheetRow(title: src.name, selected: false) {
                    vm.load(url: src.url, headers: [:], clearKey: nil)
                    showAudio = false
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private func resolveAndPlay() async {
        let urlStr = channel.playbackURL?.absoluteString ?? ""
        if ApixStreamResolverIOS.isApixStream(urlStr) {
            isResolving = true
            if let cfg = await ApixStreamResolverIOS.resolve(urlStr) {
                vm.setupFromApix(cfg, fallbackTitle: channel.name)
            } else {
                vm.setup(channel: channel)
            }
            isResolving = false
        } else {
            vm.setup(channel: channel)
        }
    }

    private func toggleCtrl() {
        withAnimation { showCtrl.toggle() }
        if showCtrl { resetTimer() }
    }

    private func resetTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 4, repeats: false) { _ in
            withAnimation { showCtrl = false }
        }
    }

    private func fmtTime(_ s: Double) -> String {
        guard s.isFinite && s > 0 else { return "00:00" }
        let h = Int(s)/3600, m = (Int(s)%3600)/60, sec = Int(s)%60
        return h > 0
            ? String(format: "%d:%02d:%02d", h, m, sec)
            : String(format: "%02d:%02d", m, sec)
    }

    private func forceLandscape() {
        if #available(iOS 16, *) {
            guard let sc = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            sc.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape))
        } else {
            UIDevice.current.setValue(UIInterfaceOrientation.landscapeRight.rawValue, forKey: "orientation")
        }
    }

    private func forcePortrait() {
        if #available(iOS 16, *) {
            guard let sc = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            sc.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait))
        } else {
            UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
        }
    }
}

// MARK: - Sub-views (مطابقة لتصميم الأندرويد)

struct CircleIconBtn: View {
    let icon: String
    var size: CGFloat = 44
    var large = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size * (large ? 0.55 : 0.5), weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Circle().fill(.white.opacity(0.12)))
                .overlay(Circle().stroke(.white.opacity(large ? 0.4 : 0.2), lineWidth: large ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

struct SmallIconBtn: View {
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
        }
        .buttonStyle(.plain)
    }
}

struct IOSProgressSlider: View {
    let value: Double
    let onEnd: (Double) -> Void
    @State private var drag: Double? = nil

    var body: some View {
        GeometryReader { g in
            let v = drag ?? value
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.25)).frame(height: 4)
                Capsule().fill(AppTheme.gold)
                    .frame(width: g.size.width * v, height: 4)
                Circle().fill(.white)
                    .frame(width: 14, height: 14)
                    .offset(x: g.size.width * v - 7)
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { d in drag = min(max(d.location.x / g.size.width, 0), 1) }
                .onEnded   { d in
                    let f = min(max(d.location.x / g.size.width, 0), 1)
                    onEnd(f); drag = nil
                }
            )
        }
        .frame(height: 20)
    }
}

struct IOSPlayerSheet<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List { content }
                .listStyle(.plain).background(AppTheme.background)
                .scrollContentBackground(.hidden)
                .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("إغلاق") { dismiss() }.foregroundStyle(AppTheme.gold)
                    }
                }
        }
        .presentationDetents([.medium])
        .preferredColorScheme(.dark)
    }
}

struct IOSSheetRow: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .foregroundStyle(selected ? AppTheme.gold : .white)
                    .fontWeight(selected ? .bold : .regular)
                Spacer()
                if selected {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(AppTheme.gold)
                }
            }
            .padding(.vertical, 4).contentShape(Rectangle())
        }
        .listRowBackground(AppTheme.surface)
    }
}

// make load() accessible
extension NativePlayerCoordinator {
    func load(url: String, headers: [String: String], clearKey: String?) {
        cleanAll()
        errorMessage = nil; isBuffering = true; currentTime = 0; duration = 0
        guard let u = URL(string: url) else { errorMessage = "رابط غير صالح"; return }
        var opts: [String: Any] = [:]
        if !headers.isEmpty { opts["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        let item = AVPlayerItem(asset: AVURLAsset(url: u, options: opts.isEmpty ? nil : opts))
        player.replaceCurrentItem(with: item)
        observe(item)
        player.play()
    }
}
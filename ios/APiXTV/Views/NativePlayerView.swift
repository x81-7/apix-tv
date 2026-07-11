import SwiftUI
import AVKit
import Combine
import UIKit

private let colorGold = Color(red: 212/255, green: 160/255, blue: 23/255)
private let colorRed = Color(red: 229/255, green: 9/255, blue: 20/255)
private let colorBgDark = Color(red: 17/255, green: 17/255, blue: 17/255)
private let colorRowSelected = Color(red: 42/255, green: 42/255, blue: 42/255)
private let colorDivider = Color(red: 34/255, green: 34/255, blue: 34/255)

struct PlayerServer: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let headers: [String: String]
    let clearKey: String?
    let qualities: [PlayerQuality]
    let subtitles: [PlayerSubtitleTrack]
    let audioSources: [PlayerAudioSource]
    let healthScore: Int

    init(name: String, url: String, headers: [String: String] = [:], clearKey: String? = nil, qualities: [PlayerQuality] = [], subtitles: [PlayerSubtitleTrack] = [], audioSources: [PlayerAudioSource] = [], healthScore: Int = 0) {
        self.name = name
        self.url = url
        self.headers = headers
        self.clearKey = clearKey
        self.qualities = qualities
        self.subtitles = subtitles
        self.audioSources = audioSources
        self.healthScore = healthScore
    }
}

struct PlayerQuality: Identifiable, Equatable {
    let id = UUID()
    let label: String
    let bitrate: Double
    let fps: Int?
    let requiredMbps: Double

    var detailText: String {
        var p = [label]
        if let fps { p.append("\(fps)fps") }
        if bitrate > 0 { p.append(String(format: "%.1f Mbps", bitrate / 1_000_000.0)) }
        return p.joined(separator: " • ")
    }
}

struct PlayerAudioSource: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let languageCode: String?
}

struct PlayerSubtitleTrack: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let languageCode: String
    var isAllowed: Bool { ["ar", "en"].contains(languageCode.lowercased()) }
}

enum PlayerIconType { case play, pause, forward, rewind, back, resize, settings, server, pip }

enum PlayerDisplayMode: CaseIterable, Equatable {
    case fit, fill, stretch, cinema
    var title: String {
        switch self {
        case .fit: return "ملاءمة"
        case .fill: return "ملء"
        case .stretch: return "تمديد"
        case .cinema: return "سينما"
        }
    }
    var gravity: AVLayerVideoGravity {
        switch self {
        case .fit: return .resizeAspect
        case .fill: return .resizeAspectFill
        case .stretch: return .resize
        case .cinema: return .resizeAspectFill
        }
    }
    var zoom: CGFloat { self == .cinema ? 1.06 : 1.0 }
    var iconName: String {
        switch self {
        case .fit: return "arrow.up.left.and.arrow.down.right"
        case .fill: return "rectangle.expand.vertical"
        case .stretch: return "arrow.left.and.right.righttriangle.left.righttriangle.right"
        case .cinema: return "tv"
        }
    }
    mutating func cycle() {
        let all = Self.allCases
        self = all[(all.firstIndex(of: self) ?? 0 + 1) % all.count]
    }
}

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
    @Published var qualities: [PlayerQuality] = NativePlayerCoordinator.defaultQualities
    @Published var currentQualityIndex = 0
    @Published var subtitleTracks: [PlayerSubtitleTrack] = []
    @Published var currentSubtitleIndex = 0
    @Published var audioSources: [PlayerAudioSource] = []
    @Published var currentAudioIndex = 0
    @Published var displayMode: PlayerDisplayMode = .fit

    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()

    static let defaultQualities: [PlayerQuality] = [
        PlayerQuality(label: "تلقائي", bitrate: 0, fps: nil, requiredMbps: 0),
        PlayerQuality(label: "4K", bitrate: 20_000_000, fps: 60, requiredMbps: 20),
        PlayerQuality(label: "1080p", bitrate: 8_000_000, fps: 60, requiredMbps: 8),
        PlayerQuality(label: "720p", bitrate: 4_000_000, fps: 60, requiredMbps: 4),
        PlayerQuality(label: "480p", bitrate: 1_500_000, fps: 30, requiredMbps: 1.5),
        PlayerQuality(label: "360p", bitrate: 800_000, fps: 30, requiredMbps: 0.8)
    ]

    init() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { print(error.localizedDescription) }
    }

    func setup(channel: Channel) {
        let primary = channel.playbackURL ?? ""
        let backup = channel.backupURL?.absoluteString ?? ""
        let head = channel.effectiveHeaders
        let clear = channel.clearKeyCombined

        var list: [PlayerServer] = []
        if !primary.isEmpty {
            list.append(PlayerServer(name: "السيرفر الأساسي", url: primary, headers: head, clearKey: clear, qualities: NativePlayerCoordinator.defaultQualities, healthScore: 100))
        }
        if !backup.isEmpty {
            list.append(PlayerServer(name: "السيرفر الاحتياطي", url: backup, headers: head, clearKey: clear, qualities: NativePlayerCoordinator.defaultQualities, healthScore: 60))
        }
        setup(title: channel.name, url: primary, headers: head, clearKey: clear, backupUrl: backup.isEmpty ? nil : backup, servers: list, qualities: NativePlayerCoordinator.defaultQualities, subtitles: [], audioSources: [])
    }

    func setup(title: String, url: String, headers: [String: String] = [:], clearKey: String? = nil, backupUrl: String? = nil, servers: [PlayerServer] = [], qualities: [PlayerQuality] = [], subtitles: [PlayerSubtitleTrack] = [], audioSources: [PlayerAudioSource] = []) {
        self.title = title
        errorMessage = nil
        isBuffering = true
        currentTime = 0
        duration = 0

        var finalServers = servers.filter { !$0.url.isEmpty }
        finalServers.sort { $0.healthScore == $1.healthScore ? $0.name < $1.name : $0.healthScore > $1.healthScore }
        if finalServers.isEmpty, !url.isEmpty {
            let q = qualities.isEmpty ? NativePlayerCoordinator.defaultQualities : qualities
            finalServers = [PlayerServer(name: "السيرفر الأساسي", url: url, headers: headers, clearKey: clearKey, qualities: q, subtitles: subtitles.filter(\.isAllowed), audioSources: audioSources, healthScore: 100)]
            if let backupUrl, !backupUrl.isEmpty {
                finalServers.append(PlayerServer(name: "السيرفر الاحتياطي", url: backupUrl, headers: headers, clearKey: clearKey, qualities: q, subtitles: subtitles.filter(\.isAllowed), audioSources: audioSources, healthScore: 60))
            }
        }
        self.servers = finalServers
        self.currentServerIndex = 0
        applyMetadata(index: 0, fallbackQualities: qualities, fallbackSubs: subtitles, fallbackAudio: audioSources)
        if !finalServers.isEmpty { loadServer(index: 0) } else { errorMessage = "No valid URLs found"; isBuffering = false }
    }

    private func applyMetadata(index: Int, fallbackQualities: [PlayerQuality], fallbackSubs: [PlayerSubtitleTrack], fallbackAudio: [PlayerAudioSource]) {
        guard servers.indices.contains(index) else { return }
        let s = servers[index]
        let q = s.qualities.isEmpty ? (fallbackQualities.isEmpty ? NativePlayerCoordinator.defaultQualities : fallbackQualities) : s.qualities
        qualities = q.sorted { $0.bitrate > $1.bitrate }
        if !qualities.contains(where: { $0.label == "تلقائي" }) {
            qualities.insert(PlayerQuality(label: "تلقائي", bitrate: 0, fps: nil, requiredMbps: 0), at: 0)
        }
        subtitleTracks = (s.subtitles.isEmpty ? fallbackSubs : s.subtitles).filter(\.isAllowed)
        audioSources = s.audioSources.isEmpty ? fallbackAudio : s.audioSources
        currentQualityIndex = 0
        currentSubtitleIndex = 0
        currentAudioIndex = 0
    }

    func loadServer(index: Int) {
        guard servers.indices.contains(index) else { return }
        currentServerIndex = index
        let s = servers[index]
        applyMetadata(index: index, fallbackQualities: [], fallbackSubs: [], fallbackAudio: [])
        load(url: s.url, headers: s.headers, clearKey: s.clearKey)
    }

    func setQuality(index: Int) { guard qualities.indices.contains(index) else { return }; currentQualityIndex = index; player.currentItem?.preferredPeakBitRate = qualities[index].bitrate }
    func setSubtitle(index: Int) { guard subtitleTracks.indices.contains(index) else { return }; currentSubtitleIndex = index }
    func setAudio(index: Int) { guard audioSources.indices.contains(index) else { return }; currentAudioIndex = index }
    func cycleDisplayMode() { displayMode.cycle() }

    func load(url: String, headers: [String: String], clearKey: String?) {
        cleanupPlaybackOnly()
        errorMessage = nil
        isBuffering = true
        currentTime = 0
        duration = 0
        guard let validUrl = URL(string: url) else { errorMessage = "Invalid stream URL"; isBuffering = false; return }
        var options: [String: Any] = [:]
        if !headers.isEmpty { options["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        let asset = AVURLAsset(url: validUrl, options: options.isEmpty ? nil : options)
        let item = AVPlayerItem(asset: asset)
        _ = clearKey
        player.replaceCurrentItem(with: item)
        setupObservers(for: item)
        player.play()
    }

    private func setupObservers(for item: AVPlayerItem) {
        item.publisher(for: \.status).receive(on: RunLoop.main).sink { [weak self] status in
            guard let self else { return }
            switch status {
            case .readyToPlay: self.isBuffering = false
            case .failed: self.errorMessage = item.error?.localizedDescription ?? "خطأ تقني في التشغيل"; self.isBuffering = false
            default: break
            }
        }.store(in: &cancellables)

        item.publisher(for: \.duration).receive(on: RunLoop.main).sink { [weak self] d in
            let s = d.seconds
            self?.duration = (s.isNaN || s.isInfinite) ? 0 : s
        }.store(in: &cancellables)

        player.publisher(for: \.timeControlStatus).receive(on: RunLoop.main).sink { [weak self] status in
            guard let self else { return }
            self.isPlaying = (status == .playing)
            self.isBuffering = (status == .waitingToPlayAtSpecifiedRate)
        }.store(in: &cancellables)

        NotificationCenter.default.publisher(for: .AVPlayerItemPlaybackStalled, object: item).receive(on: RunLoop.main).sink { [weak self] _ in
            self?.isBuffering = true
        }.store(in: &cancellables)

        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in self?.currentTime = time.seconds }
        }
    }

    func togglePlay() { player.timeControlStatus == .playing ? player.pause() : player.play() }
    func seek(by seconds: Double) { let maxTime = duration > 0 ? duration : .infinity; let target = max(0, min(currentTime + seconds, maxTime)); player.seek(to: CMTime(seconds: target, preferredTimescale: 600)) }
    func seekTo(fraction: Double) { guard duration > 0 else { return }; let target = duration * fraction; player.seek(to: CMTime(seconds: target, preferredTimescale: 600)) { [weak self] _ in Task { @MainActor in self?.currentTime = target } } }

    func cleanupPlaybackOnly() {
        if let observer = timeObserver { player.removeTimeObserver(observer); timeObserver = nil }
        cancellables.removeAll()
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
    func cleanupAll() { cleanupPlaybackOnly() }
    deinit { if let o = timeObserver { player.removeTimeObserver(o) } }
}

final class PlayerUIView: UIView { var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }; override class var layerClass: AnyClass { AVPlayerLayer.self } }

struct PlayerVideoLayer: UIViewRepresentable {
    let player: AVPlayer
    let gravity: AVLayerVideoGravity
    func makeUIView(context: Context) -> PlayerUIView { let v = PlayerUIView(); v.backgroundColor = .black; v.playerLayer.player = player; v.playerLayer.videoGravity = gravity; return v }
    func updateUIView(_ uiView: PlayerUIView, context: Context) { uiView.playerLayer.player = player; uiView.playerLayer.videoGravity = gravity }
}

struct NativePlayerView: View {
    let channel: Channel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = NativePlayerCoordinator()
    @State private var showControls = true
    @State private var hideTimer: Timer?
    @State private var isResolving = false
    @State private var showQualityDialog = false
    @State private var showServerDialog = false
    @State private var showSubtitleDialog = false
    @State private var showAudioDialog = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            PlayerVideoLayer(player: viewModel.player, gravity: viewModel.displayMode.gravity)
                .scaleEffect(viewModel.displayMode.zoom)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { toggleControls() }
                .zIndex(0)

            if viewModel.isBuffering && viewModel.errorMessage == nil && !isResolving {
                ProgressView().progressViewStyle(.circular).tint(colorRed).scaleEffect(1.6).allowsHitTesting(false).zIndex(1)
            }
            if isResolving {
                Color.black.opacity(0.6).ignoresSafeArea().zIndex(1)
                ProgressView().tint(colorRed).scaleEffect(1.6).zIndex(2)
            }
            if let error = viewModel.errorMessage {
                Color.black.opacity(0.9).ignoresSafeArea().zIndex(1)
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 64)).foregroundColor(colorRed)
                    Text(error).foregroundColor(.white).font(.system(size: 16, weight: .bold)).multilineTextAlignment(.center).padding(32)
                    PlayerIconButton(type: .back, size: 36) { viewModel.player.pause(); dismiss() }
                }.zIndex(2)
            }
            if showControls && viewModel.errorMessage == nil && !isResolving {
                controlsLayer.transition(.opacity.animation(.easeInOut(duration: 0.2))).zIndex(2)
            }
            dialogsLayer.zIndex(3)
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear { forceLandscape(); resetTimer() }
        .onDisappear { forcePortrait(); viewModel.cleanupAll(); hideTimer?.invalidate() }
        .task { await resolveAndPlay() }
    }

    private var dialogsLayer: some View {
        ZStack {
            if showQualityDialog {
                APiXDialog(title: "الجودة", isPresented: $showQualityDialog) {
                    ForEach(Array(viewModel.qualities.enumerated()), id: \.offset) { index, q in
                        APiXDialogRow(title: q.detailText, subtitle: q.requiredMbps > 0 ? "\(String(format: "%.1f", q.requiredMbps)) Mbps" : nil, isSelected: index == viewModel.currentQualityIndex) {
                            viewModel.setQuality(index: index)
                            showQualityDialog = false
                            resetTimer()
                        }
                    }
                }
            }
            if showServerDialog {
                APiXDialog(title: "السيرفرات", isPresented: $showServerDialog) {
                    ForEach(Array(viewModel.servers.enumerated()), id: \.offset) { index, s in
                        APiXDialogRow(title: s.name, subtitle: s.qualities.first?.detailText, isSelected: index == viewModel.currentServerIndex) {
                            viewModel.loadServer(index: index)
                            showServerDialog = false
                            resetTimer()
                        }
                    }
                }
            }
            if showSubtitleDialog {
                APiXDialog(title: "الترجمات", isPresented: $showSubtitleDialog) {
                    if viewModel.subtitleTracks.isEmpty {
                        APiXDialogRow(title: "لا توجد ترجمات", isSelected: false) { showSubtitleDialog = false }
                    } else {
                        ForEach(Array(viewModel.subtitleTracks.enumerated()), id: \.offset) { index, s in
                            APiXDialogRow(title: s.name, subtitle: s.languageCode.uppercased(), isSelected: index == viewModel.currentSubtitleIndex) {
                                viewModel.setSubtitle(index: index)
                                showSubtitleDialog = false
                                resetTimer()
                            }
                        }
                    }
                }
            }
            if showAudioDialog {
                APiXDialog(title: "الصوت", isPresented: $showAudioDialog) {
                    if viewModel.audioSources.isEmpty {
                        APiXDialogRow(title: "لا توجد مسارات صوت", isSelected: false) { showAudioDialog = false }
                    } else {
                        ForEach(Array(viewModel.audioSources.enumerated()), id: \.offset) { index, a in
                            APiXDialogRow(title: a.name, subtitle: a.languageCode?.uppercased(), isSelected: index == viewModel.currentAudioIndex) {
                                viewModel.setAudio(index: index)
                                showAudioDialog = false
                                resetTimer()
                            }
                        }
                    }
                }
            }
        }
    }

    private var controlsLayer: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom).frame(height: 100).allowsHitTesting(false)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom).frame(height: 120).allowsHitTesting(false)
            }.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    PlayerIconButton(type: .back, size: 36) { viewModel.player.pause(); dismiss() }
                    Spacer()
                    Text(viewModel.title).font(.system(size: 16, weight: .bold)).foregroundColor(.white).lineLimit(1).frame(maxWidth: 320, alignment: .trailing)
                }.padding(.horizontal, 16).padding(.top, 12)

                Spacer()

                VStack(spacing: 4) {
                    HStack(spacing: 8) {
                        Text(viewModel.duration > 0 ? formatTime(viewModel.currentTime) : "LIVE")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(viewModel.duration > 0 ? .white : colorRed)
                        IOSProgressSlider(value: viewModel.duration > 0 ? (viewModel.currentTime / viewModel.duration) : 0) { fraction in
                            if viewModel.duration > 0 { viewModel.seekTo(fraction: fraction) }
                            resetTimer()
                        }.frame(height: 16).disabled(viewModel.duration <= 0)
                        Text(viewModel.duration > 0 ? formatTime(viewModel.duration) : "LIVE")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(viewModel.duration > 0 ? .white : colorRed)
                    }.padding(.horizontal, 16)

                    HStack {
                        HStack(spacing: 16) {
                            PlayerIconButton(type: .rewind, size: 38) { viewModel.seek(by: -10); resetTimer() }
                            PlayerIconButton(type: viewModel.isPlaying ? .pause : .play, size: 44) { viewModel.togglePlay(); resetTimer() }
                            PlayerIconButton(type: .forward, size: 38) { viewModel.seek(by: 10); resetTimer() }
                        }
                        Spacer()
                        HStack(spacing: 12) {
                            if !viewModel.audioSources.isEmpty {
                                PlayerImageButton(systemName: "speaker.wave.2.fill", size: 32) { showAudioDialog = true; hideTimer?.invalidate() }
                            }
                            if !viewModel.subtitleTracks.isEmpty {
                                PlayerImageButton(systemName: "captions.bubble.fill", size: 32) { showSubtitleDialog = true; hideTimer?.invalidate() }
                            }
                            if viewModel.servers.count > 1 {
                                PlayerIconButton(type: .server, size: 32) { showServerDialog = true; hideTimer?.invalidate() }
                            }
                            if viewModel.qualities.count > 1 {
                                PlayerQualityButton(quality: viewModel.qualities[viewModel.currentQualityIndex]) { showQualityDialog = true; hideTimer?.invalidate() }
                            }
                            PlayerDisplayModeButton(mode: viewModel.displayMode) { viewModel.cycleDisplayMode(); resetTimer() }
                            PlayerIconButton(type: .pip, size: 32) { }
                        }
                    }.padding(.horizontal, 16).padding(.bottom, 8)
                }
            }
        }
    }

    private func resolveAndPlay() async {
        let urlString = channel.playbackURL ?? ""
        if ApixStreamResolverIOS.isApixStream(urlString) {
            isResolving = true
            if let config = await ApixStreamResolverIOS.resolve(urlString) {
                viewModel.setup(
                    title: config.title ?? channel.name,
                    url: config.url,
                    headers: config.headers,
                    clearKey: config.clearKey,
                    backupUrl: config.backupUrl
                )
            } else {
                viewModel.setup(channel: channel)
            }
            isResolving = false
        } else {
            viewModel.setup(channel: channel)
        }
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) { showControls.toggle() }
        if showControls { resetTimer() }
    }

    private func resetTimer() {
        hideTimer?.invalidate()
        hideTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in
            withAnimation(.easeInOut(duration: 0.3)) { showControls = false }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && !seconds.isNaN && seconds >= 0 else { return "00:00" }
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

struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 1.18 : 1.0).animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

struct PlayerIconButton: View {
    let type: PlayerIconType
    let size: CGFloat
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.clear).frame(width: size, height: size)
                PlayerIconPath(type: type).stroke(Color.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)).frame(width: size * 0.65, height: size * 0.65)
            }.contentShape(Circle())
        }.buttonStyle(ScaleButtonStyle())
    }
}

struct PlayerImageButton: View {
    let systemName: String
    let size: CGFloat
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Color.clear).frame(width: size, height: size)
                Image(systemName: systemName).font(.system(size: size * 0.55, weight: .semibold)).foregroundColor(.white)
            }.contentShape(Circle())
        }.buttonStyle(ScaleButtonStyle())
    }
}

struct PlayerQualityButton: View {
    let quality: PlayerQuality
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(quality.label).font(.system(size: 12, weight: .bold)).lineLimit(1)
                if quality.bitrate > 0 {
                    Text(String(format: "%.1f Mbps", quality.bitrate / 1_000_000.0)).font(.system(size: 9, weight: .medium)).opacity(0.8)
                }
            }
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white.opacity(0.10))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(colorGold.opacity(0.8), lineWidth: 1))
            )
        }.buttonStyle(ScaleButtonStyle())
    }
}

struct PlayerDisplayModeButton: View {
    let mode: PlayerDisplayMode
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: mode.iconName).font(.system(size: 12, weight: .semibold))
                Text(mode.title).font(.system(size: 9, weight: .bold)).lineLimit(1)
            }
            .foregroundColor(.white)
            .frame(width: 56, height: 32)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white.opacity(0.10))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(colorGold.opacity(0.8), lineWidth: 1))
            )
        }.buttonStyle(ScaleButtonStyle())
    }
}

struct IOSProgressSlider: View {
    let value: Double
    let onEnd: (Double) -> Void
    @State private var dragValue: Double?

    var body: some View {
        GeometryReader { geometry in
            let displayValue = dragValue ?? value
            let maxWidth = geometry.size.width
            let sliderWidth = max(0, maxWidth * CGFloat(displayValue))
            let circleOffset = max(0, min(maxWidth - 12, sliderWidth - 6))

            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.26)).frame(height: 3)
                Capsule().fill(colorRed).frame(width: sliderWidth, height: 3)
                Circle().fill(Color.white).frame(width: 12, height: 12).offset(x: circleOffset)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        dragValue = fraction
                    }
                    .onEnded { gesture in
                        let fraction = min(max(0, gesture.location.x / maxWidth), 1)
                        onEnd(fraction)
                        dragValue = nil
                    }
            )
        }
    }
}

struct APiXDialog<Content: View>: View {
    let title: String
    @Binding var isPresented: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea().onTapGesture { isPresented = false }
            VStack(spacing: 0) {
                Text(title).foregroundColor(colorGold).font(.system(size: 16, weight: .bold)).padding(16)
                Divider().background(colorDivider)
                ScrollView { VStack(spacing: 4) { content() }.padding(8) }
                Divider().background(colorDivider)
                Button { isPresented = false } label: {
                    Text("إغلاق").foregroundColor(colorGold).font(.system(size: 14, weight: .bold)).frame(maxWidth: .infinity).padding(12)
                }
            }
            .background(colorBgDark)
            .cornerRadius(12)
            .frame(width: min(UIScreen.main.bounds.width * 0.88, 420))
            .frame(maxHeight: UIScreen.main.bounds.height * 0.85)
            .shadow(color: .black.opacity(0.45), radius: 18, x: 0, y: 8)
        }
        .transition(.opacity)
    }
}

struct APiXDialogRow: View {
    let title: String
    let subtitle: String?
    let isSelected: Bool
    let action: () -> Void

    init(title: String, subtitle: String? = nil, isSelected: Bool, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.isSelected = isSelected
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundColor(isSelected ? colorGold : .white).font(.system(size: 13, weight: isSelected ? .bold : .regular)).lineLimit(1)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle).foregroundColor(.white.opacity(0.65)).font(.system(size: 11)).lineLimit(1)
                    }
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill").foregroundColor(colorGold).font(.system(size: 18))
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

struct PlayerIconPath: Shape {
    let type: PlayerIconType
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        switch type {
        case .play:
            path.move(to: CGPoint(x: 8 * sx, y: 6 * sy)); path.addLine(to: CGPoint(x: 8 * sx, y: 18 * sy)); path.addLine(to: CGPoint(x: 18 * sx, y: 12 * sy)); path.closeSubpath()
        case .pause:
            path.move(to: CGPoint(x: 6 * sx, y: 7 * sy)); path.addArc(center: CGPoint(x: 8 * sx, y: 7 * sy), radius: 2 * sx, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false); path.addLine(to: CGPoint(x: 10 * sx, y: 17 * sy)); path.addArc(center: CGPoint(x: 8 * sx, y: 17 * sy), radius: 2 * sx, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false); path.closeSubpath(); path.move(to: CGPoint(x: 14 * sx, y: 7 * sy)); path.addArc(center: CGPoint(x: 16 * sx, y: 7 * sy), radius: 2 * sx, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false); path.addLine(to: CGPoint(x: 18 * sx, y: 17 * sy)); path.addArc(center: CGPoint(x: 16 * sx, y: 17 * sy), radius: 2 * sx, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false); path.closeSubpath()
        case .forward:
            path.move(to: CGPoint(x: 9 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 14 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 9 * sx, y: 17 * sy)); path.move(to: CGPoint(x: 15 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 20 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 15 * sx, y: 17 * sy))
        case .rewind:
            path.move(to: CGPoint(x: 15 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 10 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 15 * sx, y: 17 * sy)); path.move(to: CGPoint(x: 9 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 4 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 9 * sx, y: 17 * sy))
        case .back:
            path.move(to: CGPoint(x: 19 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 5 * sx, y: 12 * sy)); path.move(to: CGPoint(x: 12 * sx, y: 19 * sy)); path.addLine(to: CGPoint(x: 5 * sx, y: 12 * sy)); path.addLine(to: CGPoint(x: 12 * sx, y: 5 * sy))
        case .resize:
            path.move(to: CGPoint(x: 15 * sx, y: 3 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 3 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 9 * sy)); path.move(to: CGPoint(x: 9 * sx, y: 21 * sy)); path.addLine(to: CGPoint(x: 3 * sx, y: 21 * sy)); path.addLine(to: CGPoint(x: 3 * sx, y: 15 * sy)); path.move(to: CGPoint(x: 21 * sx, y: 3 * sy)); path.addLine(to: CGPoint(x: 14 * sx, y: 10 * sy)); path.move(to: CGPoint(x: 3 * sx, y: 21 * sy)); path.addLine(to: CGPoint(x: 10 * sx, y: 14 * sy))
        case .server:
            path.move(to: CGPoint(x: 2 * sx, y: 16.1 * sy)); path.addArc(center: CGPoint(x: 2 * sx, y: 20 * sy), radius: 5 * sx, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false); path.move(to: CGPoint(x: 2 * sx, y: 12.05 * sy)); path.addArc(center: CGPoint(x: 2 * sx, y: 20 * sy), radius: 9 * sx, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false); path.move(to: CGPoint(x: 2 * sx, y: 8 * sy)); path.addArc(center: CGPoint(x: 2 * sx, y: 20 * sy), radius: 13 * sx, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false); path.move(to: CGPoint(x: 20 * sx, y: 4 * sy)); path.addLine(to: CGPoint(x: 4 * sx, y: 4 * sy)); path.move(to: CGPoint(x: 20 * sx, y: 4 * sy)); path.addLine(to: CGPoint(x: 20 * sx, y: 20 * sy)); path.addLine(to: CGPoint(x: 14 * sx, y: 20 * sy))
        case .pip:
            path.move(to: CGPoint(x: 9 * sx, y: 19 * sy)); path.addLine(to: CGPoint(x: 5 * sx, y: 19 * sy)); path.addLine(to: CGPoint(x: 3 * sx, y: 17 * sy)); path.addLine(to: CGPoint(x: 3 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 5 * sx, y: 5 * sy)); path.addLine(to: CGPoint(x: 19 * sx, y: 5 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 7 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 10 * sy)); path.move(to: CGPoint(x: 13 * sx, y: 13 * sy)); path.addLine(to: CGPoint(x: 19 * sx, y: 13 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 15 * sy)); path.addLine(to: CGPoint(x: 21 * sx, y: 17 * sy)); path.addLine(to: CGPoint(x: 19 * sx, y: 19 * sy)); path.addLine(to: CGPoint(x: 13 * sx, y: 19 * sy)); path.addLine(to: CGPoint(x: 11 * sx, y: 17 * sy)); path.addLine(to: CGPoint(x: 11 * sx, y: 15 * sy)); path.addLine(to: CGPoint(x: 13 * sx, y: 13 * sy)); path.closeSubpath()
        case .settings:
            path.move(to: CGPoint(x: 12 * sx, y: 8 * sy)); path.addCurve(to: CGPoint(x: 16 * sx, y: 12 * sy), control1: CGPoint(x: 14.2 * sx, y: 8 * sy), control2: CGPoint(x: 16 * sx, y: 9.8 * sy)); path.addCurve(to: CGPoint(x: 12 * sx, y: 16 * sy), control1: CGPoint(x: 16 * sx, y: 14.2 * sy), control2: CGPoint(x: 14.2 * sx, y: 16 * sy)); path.addCurve(to: CGPoint(x: 8 * sx, y: 12 * sy), control1: CGPoint(x: 9.8 * sx, y: 16 * sy), control2: CGPoint(x: 8 * sx, y: 14.2 * sy)); path.addCurve(to: CGPoint(x: 12 * sx, y: 8 * sy), control1: CGPoint(x: 8 * sx, y: 9.8 * sy), control2: CGPoint(x: 9.8 * sx, y: 8 * sy)); path.move(to: CGPoint(x: 19.4 * sx, y: 13 * sy)); path.addLine(to: CGPoint(x: 21.3 * sx, y: 14.5 * sy)); path.addLine(to: CGPoint(x: 19.4 * sx, y: 18.5 * sy)); path.addLine(to: CGPoint(x: 16.8 * sx, y: 17.5 * sy)); path.addLine(to: CGPoint(x: 14.5 * sx, y: 21 * sy)); path.addLine(to: CGPoint(x: 9.5 * sx, y: 21 * sy)); path.addLine(to: CGPoint(x: 7.2 * sx, y: 17.5 * sy)); path.addLine(to: CGPoint(x: 4.6 * sx, y: 18.5 * sy)); path.addLine(to: CGPoint(x: 2.7 * sx, y: 14.5 * sy)); path.addLine(to: CGPoint(x: 4.6 * sx, y: 13 * sy)); path.addLine(to: CGPoint(x: 4.6 * sx, y: 11 * sy)); path.addLine(to: CGPoint(x: 2.7 * sx, y: 9.5 * sy)); path.addLine(to: CGPoint(x: 4.6 * sx, y: 5.5 * sy)); path.addLine(to: CGPoint(x: 7.2 * sx, y: 6.5 * sy)); path.addLine(to: CGPoint(x: 9.5 * sx, y: 3 * sy)); path.addLine(to: CGPoint(x: 14.5 * sx, y: 3 * sy)); path.addLine(to: CGPoint(x: 16.8 * sx, y: 6.5 * sy)); path.addLine(to: CGPoint(x: 19.4 * sx, y: 5.5 * sy)); path.addLine(to: CGPoint(x: 21.3 * sx, y: 9.5 * sy)); path.addLine(to: CGPoint(x: 19.4 * sx, y: 11 * sy)); path.closeSubpath()
        }
        return path
    }
}

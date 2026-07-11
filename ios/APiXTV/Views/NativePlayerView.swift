import SwiftUI
import AVKit
import Combine
import UIKit

// MARK: - APiX palette (matches Android)
let colorGold = Color(red: 212/255, green: 160/255, blue: 23/255)      // #D4A017
let colorRed = Color(red: 229/255, green: 9/255, blue: 20/255)         // #E50914
let colorBgDark = Color(red: 17/255, green: 17/255, blue: 17/255)      // #111111
let colorRowSelected = Color(red: 42/255, green: 42/255, blue: 42/255)  // #2A2A2A
let colorDivider = Color(red: 34/255, green: 34/255, blue: 34/255)      // #222222

// MARK: - Playback Models

struct PlayerServer: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let headers: [String: String]
    let clearKey: String?
    let healthScore: Int
    let qualities: [PlayerQuality]
    let subtitles: [PlayerSubtitleTrack]
    let audioSources: [PlayerAudioSource]

    init(
        name: String,
        url: String,
        headers: [String: String] = [:],
        clearKey: String? = nil,
        healthScore: Int = 0,
        qualities: [PlayerQuality] = [],
        subtitles: [PlayerSubtitleTrack] = [],
        audioSources: [PlayerAudioSource] = []
    ) {
        self.name = name
        self.url = url
        self.headers = headers
        self.clearKey = clearKey
        self.healthScore = healthScore
        self.qualities = qualities
        self.subtitles = subtitles
        self.audioSources = audioSources
    }
}

struct PlayerQuality: Identifiable, Equatable {
    let id = UUID()
    let label: String
    let bitrate: Double
    let fps: Int?
    let requiredMbps: Double

    var detailText: String {
        var parts: [String] = [label]
        if let fps {
            parts.append("\(fps)fps")
        }
        if bitrate > 0 {
            let mbps = bitrate / 1_000_000.0
            parts.append(String(format: "%.1f Mbps", mbps))
        }
        return parts.joined(separator: " • ")
    }

    var shortLabel: String { label }
}

struct PlayerAudioSource: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let languageCode: String?

    init(name: String, url: String, languageCode: String? = nil) {
        self.name = name
        self.url = url
        self.languageCode = languageCode
    }
}

struct PlayerSubtitleTrack: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let url: String
    let languageCode: String

    var isAllowed: Bool {
        let code = languageCode.lowercased()
        return code == "ar" || code == "en"
    }
}

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

enum PlayerDisplayMode: CaseIterable, Equatable {
    case fit
    case fill
    case stretch
    case cinema

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

    var zoom: CGFloat {
        switch self {
        case .cinema: return 1.06
        default: return 1.0
        }
    }

    var iconName: String {
        switch self {
        case .fit: return "arrow.up.left.and.arrow.down.right"
        case .fill: return "rectangle.expand.vertical"
        case .stretch: return "arrow.left.and.right.righttriangle.left.righttriangle.right"
        case .cinema: return "tv"
        }
    }

    mutating func cycle() {
        let cases = Self.allCases
        guard let idx = cases.firstIndex(of: self) else {
            self = .fit
            return
        }
        self = cases[(idx + 1) % cases.count]
    }
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

    @Published var qualities: [PlayerQuality] = Self.defaultQualities
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
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .moviePlayback)
            try session.setActive(true)
        } catch {
            print("AVAudioSession setup failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Existing Channel-based flow

    func setup(channel: Channel) {
        let primaryUrlString = channel.playbackURL ?? ""
        let backupUrlString = channel.backupURL?.absoluteString

        let primary = PlayerServer(
            name: "السيرفر الأساسي",
            url: primaryUrlString,
            headers: channel.effectiveHeaders,
            clearKey: channel.clearKeyCombined,
            healthScore: 100
        )

        var servers: [PlayerServer] = []
        if !primaryUrlString.isEmpty { servers.append(primary) }

        if let backupUrlString, !backupUrlString.isEmpty {
            servers.append(PlayerServer(
                name: "السيرفر الاحتياطي",
                url: backupUrlString,
                headers: channel.effectiveHeaders,
                clearKey: channel.clearKeyCombined,
                healthScore: 60
            ))
        }

        applyPlaybackConfiguration(
            title: channel.name,
            servers: servers,
            fallbackUrl: primaryUrlString,
            fallbackHeaders: channel.effectiveHeaders,
            fallbackClearKey: channel.clearKeyCombined,
            fallbackBackupUrl: backupUrlString,
            qualities: Self.defaultQualities,
            subtitles: [],
            audioSources: []
        )
    }

    // MARK: - APiX resolved flow

    func setup(
        title: String,
        url: String,
        headers: [String: String] = [:],
        clearKey: String? = nil,
        backupUrl: String? = nil,
        servers: [PlayerServer] = [],
        qualities: [PlayerQuality] = [],
        subtitles: [PlayerSubtitleTrack] = [],
        audioSources: [PlayerAudioSource] = []
    ) {
        applyPlaybackConfiguration(
            title: title,
            servers: servers,
            fallbackUrl: url,
            fallbackHeaders: headers,
            fallbackClearKey: clearKey,
            fallbackBackupUrl: backupUrl,
            qualities: qualities,
            subtitles: subtitles,
            audioSources: audioSources
        )
    }

    private func applyPlaybackConfiguration(
        title: String,
        servers: [PlayerServer],
        fallbackUrl: String,
        fallbackHeaders: [String: String],
        fallbackClearKey: String?,
        fallbackBackupUrl: String?,
        qualities: [PlayerQuality],
        subtitles: [PlayerSubtitleTrack],
        audioSources: [PlayerAudioSource]
    ) {
        self.title = title
        self.errorMessage = nil
        self.isBuffering = true
        self.currentTime = 0
        self.duration = 0

        var finalServers = servers
            .filter { !$0.url.isEmpty }
            .sorted { lhs, rhs in
                if lhs.healthScore == rhs.healthScore { return lhs.name < rhs.name }
                return lhs.healthScore > rhs.healthScore
            }

        if finalServers.isEmpty, !fallbackUrl.isEmpty {
            finalServers = [
                PlayerServer(
                    name: "السيرفر الأساسي",
                    url: fallbackUrl,
                    headers: fallbackHeaders,
                    clearKey: fallbackClearKey,
                    healthScore: 100,
                    qualities: qualities.isEmpty ? Self.defaultQualities : qualities,
                    subtitles: subtitles.filter(\.isAllowed),
                    audioSources: audioSources
                )
            ]

            if let fallbackBackupUrl, !fallbackBackupUrl.isEmpty {
                finalServers.append(
                    PlayerServer(
                        name: "السيرفر الاحتياطي",
                        url: fallbackBackupUrl,
                        headers: fallbackHeaders,
                        clearKey: fallbackClearKey,
                        healthScore: 60,
                        qualities: qualities.isEmpty ? Self.defaultQualities : qualities,
                        subtitles: subtitles.filter(\.isAllowed),
                        audioSources: audioSources
                    )
                )
            }
        } else if !qualities.isEmpty || !subtitles.isEmpty || !audioSources.isEmpty {
            finalServers = finalServers.map { server in
                PlayerServer(
                    name: server.name,
                    url: server.url,
                    headers: server.headers,
                    clearKey: server.clearKey,
                    healthScore: server.healthScore,
                    qualities: server.qualities.isEmpty ? (qualities.isEmpty ? Self.defaultQualities : qualities) : server.qualities,
                    subtitles: server.subtitles.isEmpty ? subtitles.filter(\.isAllowed) : server.subtitles.filter(\.isAllowed),
                    audioSources: server.audioSources.isEmpty ? audioSources : server.audioSources
                )
            }
        }

        self.servers = finalServers
        self.currentServerIndex = 0

        let activeServer = finalServers.first
        let activeQualities = (activeServer?.qualities.isEmpty == false ? activeServer!.qualities : (qualities.isEmpty ? Self.defaultQualities : qualities))
            .sorted { $0.bitrate > $1.bitrate }

        self.qualities = activeQualities.contains(where: { $0.label == "تلقائي" }) ? activeQualities : ([PlayerQuality(label: "تلقائي", bitrate: 0, fps: nil, requiredMbps: 0)] + activeQualities)

        self.subtitleTracks = (activeServer?.subtitles.isEmpty == false ? activeServer!.subtitles : subtitles)
            .filter(\.isAllowed)
        self.audioSources = activeServer?.audioSources.isEmpty == false ? activeServer!.audioSources : audioSources

        self.currentQualityIndex = 0
        self.currentSubtitleIndex = 0
        self.currentAudioIndex = 0

        if !self.servers.isEmpty {
            loadServer(index: 0)
        } else {
            self.errorMessage = "No valid URLs found"
            self.isBuffering = false
        }
    }

    func loadServer(index: Int) {
        guard servers.indices.contains(index) else { return }
        currentServerIndex = index

        let selectedServer = servers[index]
        qualities = selectedServer.qualities.isEmpty ? Self.defaultQualities : selectedServer.qualities.sorted { $0.bitrate > $1.bitrate }
        if !qualities.contains(where: { $0.label == "تلقائي" }) {
            qualities.insert(PlayerQuality(label: "تلقائي", bitrate: 0, fps: nil, requiredMbps: 0), at: 0)
        }

        subtitleTracks = selectedServer.subtitles.filter(\.isAllowed)
        audioSources = selectedServer.audioSources

        currentQualityIndex = 0
        currentSubtitleIndex = 0
        currentAudioIndex = 0

        load(url: selectedServer.url, headers: selectedServer.headers, clearKey: selectedServer.clearKey)
    }

    func setQuality(index: Int) {
        guard qualities.indices.contains(index) else { return }
        currentQualityIndex = index
        let selected = qualities[index]
        player.currentItem?.preferredPeakBitRate = selected.bitrate
    }

    func setSubtitle(index: Int) {
        guard subtitleTracks.indices.contains(index) else { return }
        currentSubtitleIndex = index
        // Hook point for subtitle renderer / AVMediaSelection logic
    }

    func setAudio(index: Int) {
        guard audioSources.indices.contains(index) else { return }
        currentAudioIndex = index
        // Hook point for audio track selection logic
    }

    func cycleDisplayMode() {
        displayMode.cycle()
    }

    func load(url: String, headers: [String: String], clearKey: String?) {
        cleanupPlaybackOnly()
        errorMessage = nil
        isBuffering = true
        currentTime = 0
        duration = 0

        guard let validUrl = URL(string: url) else {
            errorMessage = "Invalid stream URL"
            isBuffering = false
            return
        }

        var options: [String: Any] = [:]
        if !headers.isEmpty {
            options[AVURLAssetHTTPHeaderFieldsKey] = headers
        }

        let asset = AVURLAsset(url: validUrl, options: options.isEmpty ? nil : options)
        let item = AVPlayerItem(asset: asset)
        _ = clearKey

        player.replaceCurrentItem(with: item)
        setupObservers(for: item)
        player.play()
    }

    private func setupObservers(for item: AVPlayerItem) {
        item.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                guard let self else { return }
                switch status {
                case .readyToPlay:
                    self.isBuffering = false
                case .failed:
                    self.errorMessage = item.error?.localizedDescription ?? "خطأ تقني في التشغيل"
                    self.isBuffering = false
                default:
                    break
                }
            }
            .store(in: &cancellables)

        item.publisher(for: \.duration)
            .receive(on: RunLoop.main)
            .sink { [weak self] dur in
                let secs = dur.seconds
                self?.duration = (secs.isNaN || secs.isInfinite) ? 0 : secs
            }
            .store(in: &cancellables)

        player.publisher(for: \.timeControlStatus)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                guard let self else { return }
                self.isPlaying = (status == .playing)
                if status == .playing {
                    self.isBuffering = false
                }
                if status == .waitingToPlayAtSpecifiedRate {
                    self.isBuffering = true
                }
            }
            .store(in: &cancellables)

        NotificationCenter.default.publisher(for: .AVPlayerItemPlaybackStalled, object: item)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.isBuffering = true
            }
            .store(in: &cancellables)

        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor in
                self?.currentTime = time.seconds
            }
        }
    }

    func togglePlay() {
        if player.timeControlStatus == .playing {
            player.pause()
        } else {
            player.play()
        }
    }

    func seek(by seconds: Double) {
        let maxTime = duration > 0 ? duration : .infinity
        let targetTime = max(0, min(currentTime + seconds, maxTime))
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        player.seek(to: target)
    }

    func seekTo(fraction: Double) {
        guard duration > 0 else { return }
        let targetTime = duration * fraction
        let target = CMTime(seconds: targetTime, preferredTimescale: 600)
        player.seek(to: target) { [weak self] _ in
            Task { @MainActor in
                self?.currentTime = targetTime
            }
        }
    }

    func cleanupPlaybackOnly() {
        if let observer = timeObserver {
            player.removeTimeObserver(observer)
            timeObserver = nil
        }
        cancellables.removeAll()
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    func cleanupAll() {
        cleanupPlaybackOnly()
    }

    deinit {
        if let observer = timeObserver {
            player.removeTimeObserver(observer)
        }
    }
}

// MARK: - Video Layer

class PlayerUIView: UIView {
    var playerLayer: AVPlayerLayer {
        layer as! AVPlayerLayer
    }

    override class var layerClass: AnyClass {
        AVPlayerLayer.self
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
    @State private var hideTimer: Timer?
    @State private var isResolving: Bool = false

    @State private var showQualityDialog: Bool = false
    @State private var showServerDialog: Bool = false
    @State private var showSubtitleDialog: Bool = false
    @State private var showAudioDialog: Bool = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            PlayerVideoLayer(player: viewModel.player, gravity: viewModel.displayMode.gravity)
                .scaleEffect(viewModel.displayMode.zoom)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    toggleControls()
                }
                .zIndex(0)

            if viewModel.isBuffering && viewModel.errorMessage == nil && !isResolving {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(colorRed)
                    .scaleEffect(1.6)
                    .allowsHitTesting(false)
                    .zIndex(1)
            }

            if isResolving {
                Color.black.opacity(0.6).ignoresSafeArea().zIndex(1)
                VStack(spacing: 16) {
                    ProgressView().tint(colorRed).scaleEffect(1.6)
                }
                .zIndex(2)
            }

            if let error = viewModel.errorMessage {
                Color.black.opacity(0.9).ignoresSafeArea().zIndex(1)
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
                        viewModel.player.pause()
                        dismiss()
                    }
                }
                .zIndex(2)
            }

            if showControls && viewModel.errorMessage == nil && !isResolving {
                controlsLayer
                    .transition(.opacity.animation(.easeInOut(duration: 0.2)))
                    .zIndex(2)
            }

            if showQualityDialog {
                APiXDialog(title: "الجودة", isPresented: $showQualityDialog) {
                    ForEach(Array(viewModel.qualities.enumerated()), id: \.offset) { index, quality in
                        APiXDialogRow(
                            title: quality.detailText,
                            isSelected: index == viewModel.currentQualityIndex
                        ) {
                            viewModel.setQuality(index: index)
                            showQualityDialog = false
                            resetTimer()
                        }
                    }
                }
                .zIndex(3)
            }

            if showServerDialog {
                APiXDialog(title: "السيرفرات", isPresented: $showServerDialog) {
                    ForEach(Array(viewModel.servers.enumerated()), id: \.offset) { index, server in
                        APiXDialogRow(
                            title: server.name,
                            subtitle: server.qualities.first?.detailText,
                            isSelected: index == viewModel.currentServerIndex
                        ) {
                            viewModel.loadServer(index: index)
                            showServerDialog = false
                            resetTimer()
                        }
                    }
                }
                .zIndex(3)
            }

            if showSubtitleDialog {
                APiXDialog(title: "الترجمات", isPresented: $showSubtitleDialog) {
                    if viewModel.subtitleTracks.isEmpty {
                        APiXDialogRow(title: "لا توجد ترجمات", isSelected: false) {
                            showSubtitleDialog = false
                        }
                    } else {
                        ForEach(Array(viewModel.subtitleTracks.enumerated()), id: \.offset) { index, subtitle in
                            APiXDialogRow(
                                title: subtitle.name,
                                subtitle: subtitle.languageCode.uppercased(),
                                isSelected: index == viewModel.currentSubtitleIndex
                            ) {
                                viewModel.setSubtitle(index: index)
                                showSubtitleDialog = false
                                resetTimer()
                            }
                        }
                    }
                }
                .zIndex(3)
            }

            if showAudioDialog {
                APiXDialog(title: "الصوت", isPresented: $showAudioDialog) {
                    if viewModel.audioSources.isEmpty {
                        APiXDialogRow(title: "لا توجد مسارات صوت", isSelected: false) {
                            showAudioDialog = false
                        }
                    } else {
                        ForEach(Array(viewModel.audioSources.enumerated()), id: \.offset) { index, audio in
                            APiXDialogRow(
                                title: audio.name,
                                subtitle: audio.languageCode?.uppercased(),
                                isSelected: index == viewModel.currentAudioIndex
                            ) {
                                viewModel.setAudio(index: index)
                                showAudioDialog = false
                                resetTimer()
                            }
                        }
                    }
                }
                .zIndex(3)
            }
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            forceLandscape()
            resetTimer()
        }
        .onDisappear {
            forcePortrait()
            viewModel.cleanupAll()
            hideTimer?.invalidate()
        }
        .task {
            await resolveAndPlay()
        }
    }

    // MARK: - Controls Layer

    private var controlsLayer: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 100)
                    .allowsHitTesting(false)

                Spacer()

                LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 120)
                    .allowsHitTesting(false)
            }
            .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    PlayerIconButton(type: .back, size: 36) {
                        viewModel.player.pause()
                        dismiss()
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
                                viewModel.seekTo(fraction: fraction)
                            }
                            resetTimer()
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
                                viewModel.seek(by: -10)
                                resetTimer()
                            }

                            PlayerIconButton(type: viewModel.isPlaying ? .pause : .play, size: 44) {
                                viewModel.togglePlay()
                                resetTimer()
                            }

                            PlayerIconButton(type: .forward, size: 38) {
                                viewModel.seek(by: 10)
                                resetTimer()
                            }
                        }

                        Spacer()

                        HStack(spacing: 12) {
                            if !viewModel.audioSources.isEmpty {
                                PlayerImageButton(systemName: "speaker.wave.2.fill", size: 32) {
                                    showAudioDialog = true
                                    hideTimer?.invalidate()
                                }
                            }

                            if !viewModel.subtitleTracks.isEmpty {
                                PlayerImageButton(systemName: "captions.bubble.fill", size: 32) {
                                    showSubtitleDialog = true
                                    hideTimer?.invalidate()
                                }
                            }

                            if viewModel.servers.count > 1 {
                                PlayerIconButton(type: .server, size: 32) {
                                    showServerDialog = true
                                    hideTimer?.invalidate()
                                }
                            }

                            if viewModel.qualities.count > 1 {
                                PlayerQualityButton(
                                    quality: viewModel.qualities[viewModel.currentQualityIndex],
                                    action: {
                                        showQualityDialog = true
                                        hideTimer?.invalidate()
                                    }
                                )
                            }

                            PlayerDisplayModeButton(
                                mode: viewModel.displayMode,
                                action: {
                                    viewModel.cycleDisplayMode()
                                    resetTimer()
                                }
                            )

                            PlayerIconButton(type: .pip, size: 32) {
                                // PiP hook
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
        withAnimation(.easeInOut(duration: 0.2)) {
            showControls.toggle()
        }
        if showControls {
            resetTimer()
        }
    }

    private func resetTimer() {
        hideTimer?.invalidate()
        hideTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in
            withAnimation(.easeInOut(duration: 0.3)) {
                showControls = false
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

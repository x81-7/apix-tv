import SwiftUI
import AVKit
import WebKit

struct RootView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var navigationPath: [Route] = []
    @State private var playerChannel: Channel?
    @State private var showingSearch = false
    @State private var pinPrompt: PinPromptContext?

    var body: some View {
        Group {
            switch viewModel.launchState {
            case .loading:
                // Branded splash so the launch experience matches Android & Windows.
                SplashView()
            case .gate:
                GateView { code in
                    _ = viewModel.verifyGate(code: code)
                }
            case .blocked(let verdict):
                BlockedView(verdict: verdict)
            case .ready:
                NavigationStack(path: $navigationPath) {
                    MainHomeView(
                        categories: viewModel.categories,
                        selectedCategoryID: viewModel.selectedCategoryID,
                        channels: viewModel.channelsForSelectedCategory(),
                        showSettingsSection: viewModel.appSettings.showSettingsSection,
                        onSelectCategory: { viewModel.selectedCategoryID = $0 },
                        onSelectChannel: handleChannelTap,
                        onOpenSearch: { showingSearch = true },
                        onOpenSettings: { navigationPath.append(.settings) }
                    )
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .submenu(let title, let menuID):
                            SubChannelsView(title: title, channels: viewModel.subChannels(for: menuID), onSelectChannel: handleChannelTap)
                        case .settings:
                            SettingsView(telegramURL: viewModel.gateConfig.telegramURL)
                        }
                    }
                    .sheet(isPresented: $showingSearch) {
                        SearchView(query: $viewModel.searchQuery, channels: viewModel.allSearchResults(), onSelectChannel: { ch in
                            showingSearch = false
                            handleChannelTap(ch)
                        })
                    }
                    .fullScreenCover(item: $playerChannel) { channel in
                        if channel.usesWebView {
                            WebPlayerView(channel: channel)
                        } else {
                            NativePlayerView(channel: channel)
                        }
                    }
                    .sheet(item: $pinPrompt) { ctx in
                        PinPromptView(
                            title: ctx.title,
                            expected: ctx.expectedPin,
                            onSuccess: {
                                pinPrompt = nil
                                ctx.onUnlock()
                            },
                            onCancel: { pinPrompt = nil }
                        )
                    }
                }
            }
        }
        .task { await viewModel.bootstrap() }
    }

    private func handleChannelTap(_ channel: Channel) {
        // PIN protection on the channel itself
        if let pin = channel.pinCode, !pin.isEmpty {
            pinPrompt = PinPromptContext(title: channel.name, expectedPin: pin) { [channel] in
                openChannel(channel)
            }
            return
        }
        openChannel(channel)
    }

    private func openChannel(_ channel: Channel) {
        // iOS-specific action type wins over the generic actionType.
        let action = (channel.iosActionType?.lowercased()).flatMap { $0.isEmpty ? nil : $0 }
            ?? channel.actionType
            ?? "direct_play"

        switch action {
        case "external", "external_link":
            let target = channel.externalUrl ?? channel.playbackURL
            if let value = target, let url = URL(string: value) {
                UIApplication.shared.open(url)
            }
        case "open_submenu":
            if let menuID = channel.sideMenuId {
                openSubmenu(title: channel.name, menuID: menuID)
            }
        default:    // direct_play / native / webview — picks player via Channel.usesWebView
            playerChannel = channel
        }
    }

    private func openSubmenu(title: String, menuID: String) {
        if let pin = viewModel.sideMenuPin(menuID),
           !pin.isEmpty,
           !viewModel.isMenuUnlocked(menuID) {
            pinPrompt = PinPromptContext(title: title, expectedPin: pin) { [menuID, title] in
                viewModel.unlockMenu(menuID)
                navigationPath.append(.submenu(title: title, menuID: menuID))
            }
            return
        }
        navigationPath.append(.submenu(title: title, menuID: menuID))
    }
}

enum Route: Hashable {
    case submenu(title: String, menuID: String)
    case settings
}

struct PinPromptContext: Identifiable {
    let id = UUID()
    let title: String
    let expectedPin: String
    let onUnlock: () -> Void
}

struct PinPromptView: View {
    let title: String
    let expected: String
    let onSuccess: () -> Void
    let onCancel: () -> Void
    @State private var entry = ""
    @State private var error: String?

    var body: some View {
        VStack(spacing: 16) {
            Text("هذه القناة محمية برمز")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
            Text(title)
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(AppTheme.gold)
                .multilineTextAlignment(.center)
            SecureField("أدخل الرمز", text: $entry)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .padding(14)
                .background(AppTheme.surface)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 20)
            if let error {
                Text(error).foregroundStyle(.red).font(.caption)
            }
            HStack(spacing: 12) {
                Button("إلغاء", action: onCancel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(AppTheme.card)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                Button("فتح") {
                    if entry == expected {
                        onSuccess()
                    } else {
                        error = "رمز غير صحيح"
                        entry = ""
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(AppTheme.gold)
                .foregroundStyle(.black)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal, 20)
        }
        .padding(.vertical, 28)
        .background(AppTheme.background.ignoresSafeArea())
        .presentationDetents([.medium])
    }
}

struct MainHomeView: View {
    let categories: [CategorySection]
    let selectedCategoryID: String?
    let channels: [Channel]
    let showSettingsSection: Bool
    let onSelectCategory: (String) -> Void
    let onSelectChannel: (Channel) -> Void
    let onOpenSearch: () -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("APiX TV")
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer()
                Button(action: onOpenSearch) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.white)
                        .font(.system(size: 20, weight: .bold))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if let title = categories.first(where: { $0.id == selectedCategoryID })?.name {
                HStack {
                    Text(title.uppercased())
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundStyle(.white)
                    Spacer()
                }
                .padding(.horizontal, 16)
            }

            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(channels) { channel in
                        ChannelCardView(channel: channel, onTap: { onSelectChannel(channel) })
                    }
                }
                .padding(12)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories) { category in
                        CategoryChip(title: category.name, isSelected: category.id == selectedCategoryID) {
                            onSelectCategory(category.id)
                        }
                    }
                    if showSettingsSection {
                        CategoryChip(title: "الإعدادات", isSelected: false, action: onOpenSettings)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .background(AppTheme.surface)
        }
        .background(AppTheme.background.ignoresSafeArea())
    }
}

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(isSelected ? .black : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isSelected ? AppTheme.gold : AppTheme.card)
                .clipShape(Capsule())
        }
    }
}

struct ChannelCardView: View {
    let channel: Channel
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: channel.imageUrl ?? "")) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Rectangle().fill(AppTheme.card)
                }
                .frame(height: 108)
                .frame(maxWidth: .infinity)
                .clipped()

                LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .center, endPoint: .bottom)
                Text(channel.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(10)
            }
            .background(AppTheme.card)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.gold.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct SubChannelsView: View {
    let title: String
    let channels: [Channel]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(channels) { channel in
                    ChannelCardView(channel: channel, onTap: { onSelectChannel(channel) })
                }
            }
            .padding(12)
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .background(AppTheme.background.ignoresSafeArea())
    }
}

struct SearchView: View {
    @Binding var query: String
    let channels: [Channel]
    let onSelectChannel: (Channel) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                TextField("ابحث عن قناة...", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(14)
                    .background(AppTheme.surface)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        ForEach(channels) { channel in
                            ChannelCardView(channel: channel) {
                                dismiss()
                                onSelectChannel(channel)
                            }
                        }
                    }
                    .padding(12)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("إغلاق") { dismiss() }
                        .foregroundStyle(AppTheme.gold)
                }
            }
            .background(AppTheme.background.ignoresSafeArea())
        }
    }
}

struct SettingsView: View {
    let telegramURL: String

    var body: some View {
        VStack(spacing: 16) {
            SettingsRow(title: "Telegram Channel", url: telegramURL)
            SettingsRow(title: "Contact Support", url: telegramURL)
            Spacer()
        }
        .padding(20)
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("SETTINGS")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SettingsRow: View {
    let title: String
    let url: String

    var body: some View {
        Button {
            if let link = URL(string: url) { UIApplication.shared.open(link) }
        } label: {
            HStack {
                Text(title)
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.forward")
                    .foregroundStyle(AppTheme.gold)
            }
            .padding(16)
            .background(AppTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

struct GateView: View {
    let onSubmitCode: (String) -> Void
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var name = ""
    @State private var link = ""
    @State private var userAgent = ""
    @State private var referer = ""
    @State private var clearKey = ""
    @State private var errorMessage: String?
    @State private var previewChannel: Channel?

    var body: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 20)
            Text(viewModel.gateConfig.title)
                .font(.system(size: 32, weight: .heavy))
                .foregroundStyle(.white)
            if !viewModel.gateConfig.subtitle.isEmpty {
                Text(viewModel.gateConfig.subtitle)
                    .foregroundStyle(AppTheme.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
            Group {
                GateField(title: "اسم القناة", text: $name)
                GateField(title: "رابط القناة", text: $link)
                GateField(title: "User Agent", text: $userAgent)
                GateField(title: "Referrer", text: $referer)
                GateField(title: "ClearKey", text: $clearKey)
            }
            .padding(.horizontal, 18)

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
            }

            Button {
                handlePrimaryAction()
            } label: {
                Text("تشغيل")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(AppTheme.gold)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 18)

            Button("Telegram") {
                if let url = URL(string: viewModel.gateConfig.telegramURL) {
                    UIApplication.shared.open(url)
                }
            }
            .foregroundStyle(AppTheme.gold)

            Spacer()
        }
        .background(AppTheme.background.ignoresSafeArea())
        .fullScreenCover(item: $previewChannel) { channel in
            if channel.usesWebView {
                WebPlayerView(channel: channel)
            } else {
                NativePlayerView(channel: channel)
            }
        }
    }

    private func handlePrimaryAction() {
        let candidateCode = !link.isEmpty ? link : name
        if !viewModel.gateConfig.bypassCode.isEmpty && candidateCode == viewModel.gateConfig.bypassCode {
            onSubmitCode(candidateCode)
            return
        }
        guard !link.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "أدخل رابط أو كود الدخول"
            return
        }
        errorMessage = nil
        let drm: DRMConfig?
        if clearKey.contains(":") {
            let parts = clearKey.split(separator: ":", maxSplits: 1).map(String.init)
            drm = DRMConfig(clearKeyId: parts.first, clearKeyKey: parts.count > 1 ? parts[1] : nil, clearKeyCombined: clearKey, clearKeyUrl: nil, clearKeyMode: "combined")
        } else {
            drm = nil
        }
        previewChannel = Channel(
            id: UUID().uuidString,
            name: name.isEmpty ? "بث يدوي" : name,
            imageUrl: nil,
            sortOrder: 0,
            actionType: "direct_play",
            hidden: false,
            sideMenuId: nil,
            externalUrl: nil,
            preferredPlayer: nil,
            iosActionType: link.lowercased().contains(".mpd") ? "webview" : "native",
            webStream: StreamConfig(url: link, userAgent: userAgent.nilIfEmpty, referrer: referer.nilIfEmpty, cookies: nil, origin: nil, drm: drm),
            androidStream: nil,
            iosStream: nil,
            androidActionType: nil,
            categoryId: nil,
            pinCode: nil,
            offlineCacheEnabled: false,
            cacheVersion: nil,
            forcedAspectRatio: nil,
            lockAspectRatio: nil
        )
    }
}

struct GateField: View {
    let title: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .foregroundStyle(.white)
                .font(.system(size: 14, weight: .semibold))
            TextField(title, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(14)
                .background(AppTheme.surface)
                .foregroundStyle(.white)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.gold.opacity(0.35), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

struct BlockedView: View {
    let verdict: BanVerdict

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text(verdict.message ?? "تم حظر هذا الجهاز")
                .font(.system(size: 24, weight: .heavy))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Text(KeychainDeviceID.get())
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(AppTheme.gold)
                .padding(12)
                .background(AppTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            Button("نسخ ID الحظر") {
                UIPasteboard.general.string = KeychainDeviceID.get()
            }
            .foregroundStyle(.black)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(AppTheme.gold)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            if let link = verdict.telegram_url, let url = URL(string: link) {
                Button("الدعم") { UIApplication.shared.open(url) }
                    .foregroundStyle(AppTheme.gold)
            }
            Spacer()
        }
        .background(AppTheme.background.ignoresSafeArea())
    }
}

struct NativePlayerView: View {
    let channel: Channel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let urlString = channel.playbackURL, let url = URL(string: urlString) {
                AVPlayerContainer(
                    url: url,
                    backupURL: channel.backupURL,
                    subtitleURL: channel.subtitleURL,
                    headers: channel.effectiveHeaders
                )
                    .ignoresSafeArea()
            }
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(Circle().fill(Color.black.opacity(0.55)))
                    .overlay(Circle().stroke(AppTheme.gold.opacity(0.6), lineWidth: 1))
                    .padding(16)
            }
        }
        .background(.black)
        .statusBarHidden(true)
    }
}

struct AVPlayerContainer: UIViewControllerRepresentable {
    let url: URL
    let backupURL: URL?
    let subtitleURL: URL?
    let headers: [String: String]

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let item = makePlayerItem(for: url)
        let player = AVPlayer(playerItem: item)
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.allowsPictureInPicturePlayback = true
        controller.videoGravity = .resizeAspect
        // Coordinator handles backup-URL fallback if the primary URL fails.
        context.coordinator.attach(player: player, primary: url, backup: backupURL, headers: headers)
        player.play()
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    private func makePlayerItem(for url: URL) -> AVPlayerItem {
        var options: [String: Any] = [:]
        if !headers.isEmpty {
            options["AVURLAssetHTTPHeaderFieldsKey"] = headers
        }
        let asset = AVURLAsset(url: url, options: options)
        let item = AVPlayerItem(asset: asset)
        return item
    }

    final class Coordinator: NSObject {
        private weak var player: AVPlayer?
        private var primary: URL?
        private var backup: URL?
        private var headers: [String: String] = [:]
        private var didFailover = false

        func attach(player: AVPlayer, primary: URL, backup: URL?, headers: [String: String]) {
            self.player = player
            self.primary = primary
            self.backup = backup
            self.headers = headers
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(onItemFailed(_:)),
                name: .AVPlayerItemFailedToPlayToEndTime,
                object: nil
            )
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(onItemFailed(_:)),
                name: .AVPlayerItemNewErrorLogEntry,
                object: nil
            )
        }

        @objc private func onItemFailed(_ note: Notification) {
            guard !didFailover, let backup, let player else { return }
            didFailover = true
            var options: [String: Any] = [:]
            if !headers.isEmpty { options["AVURLAssetHTTPHeaderFieldsKey"] = headers }
            let asset = AVURLAsset(url: backup, options: options)
            let item = AVPlayerItem(asset: asset)
            player.replaceCurrentItem(with: item)
            player.play()
        }

        deinit { NotificationCenter.default.removeObserver(self) }
    }
}

struct WebPlayerView: View {
    let channel: Channel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topTrailing) {
            PlayerWebView(channel: channel)
                .ignoresSafeArea()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(Circle().fill(Color.black.opacity(0.55)))
                    .overlay(Circle().stroke(AppTheme.gold.opacity(0.6), lineWidth: 1))
                    .padding(16)
            }
        }
        .background(.black)
        .statusBarHidden(true)
    }
}

struct PlayerWebView: UIViewRepresentable {
    let channel: Channel

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.backgroundColor = .black
        let headers = channel.effectiveHeaders
        if let ua = headers["User-Agent"], !ua.isEmpty {
            webView.customUserAgent = ua
        }
        let baseURLString = headers["Referer"]
            ?? channel.iosStream?.referrer
            ?? channel.webStream?.referrer
            ?? "https://apix.tv/"
        webView.loadHTMLString(html, baseURL: URL(string: baseURLString))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    private var html: String {
        let url = channel.playbackURL ?? ""
        let isMpd = url.lowercased().contains(".mpd")
        let drmCombined: String = channel.clearKeyCombined ?? ""
        let backup: String = channel.backupURL?.absoluteString ?? ""
        let subtitle: String = channel.subtitleURL?.absoluteString ?? ""
        let payload: [String: Any] = [
            "url": url,
            "ck": drmCombined,
            "isMpd": isMpd,
            "backup": backup,
            "subtitle": subtitle
        ]
        let data = try? JSONSerialization.data(withJSONObject: payload)
        let json = String(data: data ?? Data("{}".utf8), encoding: .utf8) ?? "{}"
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
          <style>
            html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
            video{width:100%;height:100%;object-fit:contain;background:#000;pointer-events:none;outline:none}
            video::-webkit-media-controls,
            video::-webkit-media-controls-start-playback-button,
            video::-webkit-media-controls-overlay-play-button,
            video::-webkit-media-controls-panel{display:none !important;-webkit-appearance:none !important;opacity:0 !important}
            #cover{position:fixed;inset:0;background:#000;z-index:9999;transition:opacity .25s ease}
            #cover.hidden{opacity:0;pointer-events:none}
          </style>
          <script src="https://cdn.jsdelivr.net/npm/shaka-player@4.9.9/dist/shaka-player.compiled.min.js"></script>
          <script src="https://cdn.dashjs.org/latest/dash.all.min.js"></script>
        </head>
        <body>
          <div id="cover"></div>
          <video id="video" autoplay playsinline muted preload="auto" disablepictureinpicture controlslist="nodownload nofullscreen"></video>
          <script>
            const config = \(json);
            const video = document.getElementById('video');
            const cover = document.getElementById('cover');
            video.addEventListener('playing', () => { video.muted = false; cover.classList.add('hidden'); }, { once: true });
            let usedBackup = false;

            function attachSubtitle() {
              if (!config.subtitle) return;
              const t = document.createElement('track');
              t.kind = 'subtitles';
              t.label = 'Default';
              t.srclang = 'ar';
              t.default = true;
              t.src = config.subtitle;
              video.appendChild(t);
            }

            function hexToB64Url(hex){let s='';for(let i=0;i<hex.length;i+=2){s+=String.fromCharCode(parseInt(hex.substr(i,2),16));}return btoa(s).replace(/\\+/g,'-').replace(/\\//g,'_').replace(/=/g,'');}

            async function startShaka(srcUrl){
              shaka.polyfill.installAll();
              const player = new shaka.Player(video);
              const drm = { clearKeys: {} };
              if (config.ck && config.ck.includes(':')) {
                const parts = config.ck.split(':');
                const kid = parts[0].replace(/[^a-fA-F0-9]/g,'');
                const key = parts[1].replace(/[^a-fA-F0-9]/g,'');
                drm.clearKeys[kid] = key;
              }
              player.configure({ drm });
              await player.load(srcUrl);
              video.play();
            }

            function startDash(srcUrl){
              const player = dashjs.MediaPlayer().create();
              if (config.ck && config.ck.includes(':')) {
                const parts = config.ck.split(':');
                const kid = parts[0].replace(/[^a-fA-F0-9]/g,'');
                const key = parts[1].replace(/[^a-fA-F0-9]/g,'');
                if (kid && key) {
                  let ck = {};
                  ck[hexToB64Url(kid)] = hexToB64Url(key);
                  player.setProtectionData({ 'org.w3.clearkey': { clearkeys: ck } });
                }
              }
              player.updateSettings({ streaming: { retryAttempts: { MPD: 5, MediaSegment: 5 } } });
              player.initialize(video, srcUrl, true);
            }

            async function start(srcUrl){
              try {
                if (config.isMpd) { startDash(srcUrl); }
                else { await startShaka(srcUrl); }
              } catch(e) {
                if (!usedBackup && config.backup) {
                  usedBackup = true;
                  return start(config.backup);
                }
                document.body.innerHTML += '<div style="position:fixed;bottom:20px;left:20px;color:#fff;background:rgba(0,0,0,.7);padding:10px;border-radius:8px;font-family:sans-serif">فشل التشغيل: '+(e.message||e)+'</div>';
              }
            }
            attachSubtitle();
            start(config.url);
          </script>
        </body>
        </html>
        """
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

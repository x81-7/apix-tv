import SwiftUI

@main
struct APiXTVApp: App {
    @StateObject private var viewModel = AppViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(viewModel)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    handleExternal(url: url)
                }
        }
    }

    private func handleExternal(url: URL) {
        guard let payload = ExternalLinkDecoder.extractPayload(from: url),
              let stream  = ExternalLinkDecoder.decode(payload: payload) else { return }
        viewModel.openExternalStream(stream)
    }
}

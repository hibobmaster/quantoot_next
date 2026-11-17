import SwiftUI
import QuanTootShared

@main
struct QuanTootApplication: App {
    @Environment(\.scenePhase) private var scenePhase
    private let platform = IosPlatformServices()

    var body: some Scene {
        WindowGroup {
            SharedView(platform: platform)
                .ignoresSafeArea()
                .onOpenURL { platform.handleRedirect(url: $0.absoluteString) }
                .onChange(of: scenePhase) { phase in
                    platform.setForeground(value: phase == .active)
                }
        }
    }
}

private struct SharedView: UIViewControllerRepresentable {
    let platform: IosPlatformServices

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(platform: platform)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

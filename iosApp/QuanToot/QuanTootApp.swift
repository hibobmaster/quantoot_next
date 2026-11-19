import SwiftUI
import QuanTootShared

@main
struct QuanTootApplication: App {
    var body: some Scene {
        WindowGroup { QuanTootScene() }
    }
}

private struct QuanTootScene: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var platform = IosPlatformServices()

    var body: some View {
        SharedView(platform: platform)
            .ignoresSafeArea()
            .onOpenURL { platform.handleRedirect(url: $0.absoluteString) }
            .onChange(of: scenePhase) { phase in
                platform.setForeground(value: phase == .active)
            }
    }
}

private struct SharedView: UIViewControllerRepresentable {
    let platform: IosPlatformServices

    func makeUIViewController(context: Context) -> UIViewController {
        if #available(iOS 27.1, *), UIDevice.current.userInterfaceIdiom == .phone {
            return NativeTabController(platform: platform)
        }
        return AdaptiveHost(platform: platform)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

final class AdaptiveHost: UIViewController {
    private let platform: IosPlatformServices
    private let navigation: NativeNavigation?
    private weak var hostingWindow: UIWindow?

    init(platform: IosPlatformServices, navigation: NativeNavigation? = nil) {
        self.platform = platform
        self.navigation = navigation
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        platform.setWindowProvider { [weak self] in self?.viewIfLoaded?.window ?? self?.hostingWindow }
        let media: ((UIViewController, UIViewController, String, @escaping () -> KotlinUnit) -> UIViewController)?
        if #available(iOS 27.1, *), navigation != nil {
            media = { player, controls, closeTitle, dismiss in
                MediaArrangementController(player: player, controls: controls, closeTitle: closeTitle) { _ = dismiss() }
            }
        } else {
            media = nil
        }
        let content = MainViewControllerKt.MainViewController(
            platform: platform, navigation: navigation, createMediaArrangement: media
        )
        addChild(content)
        view.addSubview(content.view)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        content.didMove(toParent: self)
        if #available(iOS 27.1, *) {
            registerForTraitChanges(UITraitCollection.systemTraitsAffectingVerticalBarEdge) { (host: AdaptiveHost, _) in
                host.updateWindowRegions()
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateWindowRegions()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateWindowRegions()
    }

    private func updateWindowRegions() {
        guard let window = view.window else { return }
        hostingWindow = window
        guard #available(iOS 27.1, *) else { return }
        let scale = traitCollection.displayScale
        func region(_ frame: CGRect, separates: Bool) -> WindowRegion {
            WindowRegion(left: Float(frame.minX * scale), top: Float(frame.minY * scale),
                         right: Float(frame.maxX * scale), bottom: Float(frame.maxY * scale),
                         separates: separates)
        }
        var regions = view.reservedRegions(kind: .division).filter(\.isActive).map {
            region(view.convert($0.frame, to: window), separates: true)
        }
        regions += view.reservedRegions(kind: .occlusion).filter(\.isActive).map {
            region(view.convert($0.frame, to: window), separates: false)
        }
        let leadingIsLeft = view.effectiveUserInterfaceLayoutDirection == .leftToRight
        let controls: VerticalControls? = switch traitCollection.verticalBarEdge {
        case .leading: leadingIsLeft ? .left : .right
        case .trailing: leadingIsLeft ? .right : .left
        default: nil
        }
        platform.updateWindowRegions(regions: regions, controls: controls)
    }
}

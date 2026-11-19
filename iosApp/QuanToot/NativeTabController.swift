import UIKit
import QuanTootShared

@available(iOS 27.1, *)
final class NativeTabController: UITabBarController, UITabBarControllerDelegate {
    private enum Destination: String, CaseIterable {
        case Home, Explore, Notifications, Profile

        var symbol: String {
            switch self {
            case .Home: "house"
            case .Explore: "magnifyingglass"
            case .Notifications: "bell"
            case .Profile: "person.crop.circle"
            }
        }
    }

    private let platform: IosPlatformServices
    private var state: NativeNavigationState?
    private var refreshScheduled = false
    private lazy var navigation = NativeNavigation { [weak self] state in
        guard let self else { return }
        self.state = state
        guard !refreshScheduled else { return }
        refreshScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.refreshScheduled = false
            self?.updateBars()
        }
    }
    private lazy var workspace = AdaptiveHost(platform: platform, navigation: navigation)
    private let accent = UIColor { traits in
        if traits.userInterfaceStyle == .dark {
            UIColor(red: 1, green: 156.0 / 255, blue: 130.0 / 255, alpha: 1)
        } else {
            UIColor(red: 183.0 / 255, green: 64.0 / 255, blue: 41.0 / 255, alpha: 1)
        }
    }

    init(platform: IosPlatformServices) {
        self.platform = platform
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        delegate = self
        view.tintColor = accent
        tabs = Destination.allCases.map { destination in
            let tab = UITab(
                title: destination.rawValue, image: UIImage(systemName: destination.symbol),
                identifier: destination.rawValue
            ) { _ in
                let page = UIViewController()
                let stack = UINavigationController(rootViewController: page)
                stack.setNavigationBarHidden(true, animated: false)
                return stack
            }
            tab.preferredPlacement = .fixed
            return tab
        }
        selectedTab = tabs.first
        setTabBarHidden(true, animated: false)
        updateBars()
    }

    func tabBarController(_ tabBarController: UITabBarController, didSelectTab tab: UITab, previousTab: UITab?) {
        _ = mountWorkspace(in: tab)
        navigation.select(id: tab.identifier)
    }

    private func updateBars() {
        if let state {
            for destination in state.destinations {
                let destinationTab = tab(forIdentifier: destination.id)
                if destinationTab?.title != destination.title { destinationTab?.title = destination.title }
            }
            let count = Int(state.unread)
            let badge = count == 0 ? nil : count > 99 ? "99+" : String(count)
            let notifications = tab(forIdentifier: Destination.Notifications.rawValue)
            if notifications?.badgeValue != badge { notifications?.badgeValue = badge }
            if selectedTab?.identifier != state.selected, let selected = tab(forIdentifier: state.selected) {
                selectedTab = selected
            }
        }

        guard let stack = mountWorkspace(in: selectedTab), let page = stack.topViewController else { return }
        let hideTabs = state?.showsTabs != true
        if isTabBarHidden != hideTabs { setTabBarHidden(hideTabs, animated: false) }
        let hideNavigation = state == nil
        if stack.isNavigationBarHidden != hideNavigation {
            stack.setNavigationBarHidden(hideNavigation, animated: false)
        }
        if let state { configure(page.navigationItem, for: state) }
    }

    private func mountWorkspace(in tab: UITab?) -> UINavigationController? {
        guard let stack = tab?.viewController as? UINavigationController,
              let page = stack.topViewController else { return nil }
        if workspace.parent !== page {
            // Keep one Compose owner for the route, drafts, and list positions across native tab switches.
            if workspace.parent != nil {
                workspace.willMove(toParent: nil)
                workspace.view.removeFromSuperview()
                workspace.removeFromParent()
            }
            page.addChild(workspace)
            workspace.view.translatesAutoresizingMaskIntoConstraints = false
            page.view.addSubview(workspace.view)
            NSLayoutConstraint.activate([
                workspace.view.leadingAnchor.constraint(equalTo: page.view.leadingAnchor),
                workspace.view.trailingAnchor.constraint(equalTo: page.view.trailingAnchor),
                workspace.view.topAnchor.constraint(equalTo: page.view.topAnchor),
                workspace.view.bottomAnchor.constraint(equalTo: page.view.bottomAnchor),
            ])
            workspace.didMove(toParent: page)
        }

        return stack
    }

    private func configure(_ item: UINavigationItem, for state: NativeNavigationState) {
        item.title = state.title
        item.subtitle = state.subtitle
        item.verticalBarCompressionBehavior = state.editing ? .prefersBarItems : .prefersTabBar

        let actions = state.actions
        item.leftBarButtonItems = actions.filter { $0.action == .back || $0.action == .close }.map(barButton)
        item.trailingItemGroups = actions.filter { $0.action == .compose }.map {
            UIBarButtonItemGroup(barButtonItems: [barButton($0)], representativeItem: nil)
        }
        item.pinnedTrailingGroup = actions.first { $0.action == .confirm }.map {
            UIBarButtonItemGroup(barButtonItems: [barButton($0)], representativeItem: nil)
        }
        let overflow = actions.filter { $0.action == .accounts || $0.action == .settings }.map(menuAction)
        item.additionalOverflowItems = overflow.isEmpty ? nil : UIDeferredMenuElement.uncached { $0(overflow) }
    }

    private func barButton(_ action: NativeToolbarAction) -> UIBarButtonItem {
        let button = UIBarButtonItem(primaryAction: menuAction(action))
        button.title = action.title
        button.accessibilityLabel = action.title
        button.isEnabled = action.enabled
        button.visibilityPriority = .high
        if action.action == .confirm {
            button.style = .prominent
            button.tintColor = accent
        }
        return button
    }

    private func menuAction(_ action: NativeToolbarAction) -> UIAction {
        let symbol: String = switch action.action {
        case .back: "chevron.backward"
        case .close: "xmark"
        case .compose: "square.and.pencil"
        case .accounts: "person.crop.circle"
        case .settings: "gearshape"
        case .confirm: "checkmark"
        default: "circle"
        }
        return UIAction(title: action.title, image: UIImage(systemName: symbol),
                        attributes: action.enabled ? [] : .disabled) { [weak self] _ in
            self?.navigation.perform(action: action.action)
        }
    }
}

@available(iOS 27.1, *)
final class MediaArrangementController: UIViewController {
    private final class MediaCanvas: UIView {
        weak var closeButton: UIView?
        weak var controls: UIView?
        weak var player: UIView?

        override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
            guard isUserInteractionEnabled, !isHidden, alpha > 0.01,
                  self.point(inside: point, with: event) else { return nil }
            // The native overlay can give its primary view the entire canvas.
            for target in [closeButton, controls, player].compactMap({ $0 }) {
                if let hit = target.hitTest(target.convert(point, from: self), with: event) {
                    return hit
                }
            }
            return super.hitTest(point, with: event)
        }
    }

    private let arrangement = UIArrangementViewController()
    private let canvas = MediaCanvas()
    private let player: UIViewController
    private let panel: MediaControlsHost
    private let closeTitle: String
    private let close: () -> Void

    init(player: UIViewController, controls: UIViewController, closeTitle: String, dismiss: @escaping () -> Void) {
        self.player = player
        panel = MediaControlsHost(controls)
        self.closeTitle = closeTitle
        close = dismiss
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var preferredVerticalBarBehavior: UIVerticalBarBehavior { .disabled }

    override func loadView() { view = canvas }

    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = .dark
        view.backgroundColor = .black
        addChild(arrangement)
        view.addSubview(arrangement.view)
        arrangement.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            arrangement.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            arrangement.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            arrangement.view.topAnchor.constraint(equalTo: view.topAnchor),
            arrangement.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        arrangement.setViewController(panel, for: .primary)
        arrangement.setViewController(player, for: .secondary)
        arrangement.updateArrangement(.overlay)
        arrangement.didMove(toParent: self)
        canvas.controls = panel.controlsView
        canvas.player = player.view

        let dismiss = UIButton(type: .close)
        dismiss.translatesAutoresizingMaskIntoConstraints = false
        dismiss.accessibilityLabel = closeTitle
        dismiss.addAction(UIAction { [weak self] _ in self?.close() }, for: .touchUpInside)
        view.addSubview(dismiss)
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            dismiss.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 4),
            dismiss.topAnchor.constraint(equalTo: safe.topAnchor, constant: 2),
            dismiss.widthAnchor.constraint(equalToConstant: 44),
            dismiss.heightAnchor.constraint(equalToConstant: 44),
        ])
        canvas.closeButton = dismiss
    }

    override func accessibilityPerformEscape() -> Bool {
        close()
        return true
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        arrangement.view.layoutIfNeeded()
        let overlays = (arrangement.state(for: .primary)?.zIndex ?? 0) > 0
        panel.setFloating(overlays)
        let controlsFrame = panel.frameOfControls(in: player.view)
        let clearance = overlays && !controlsFrame.isEmpty && controlsFrame.midY > player.view.bounds.midY
            ? max(0, player.view.bounds.maxY - controlsFrame.minY - view.safeAreaInsets.bottom)
            : 0
        if player.additionalSafeAreaInsets.bottom != clearance {
            player.additionalSafeAreaInsets.bottom = clearance
        }
    }
}

private final class MediaControlsHost: UIViewController {
    private let content: UIViewController
    private var floating = false
    private var placement: [NSLayoutConstraint] = []
    var controlsView: UIView { content.view }

    init(_ content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        content.view.clipsToBounds = true
        content.view.layer.cornerCurve = .continuous
        view.addSubview(content.view)
        updatePlacement()
        content.didMove(toParent: self)
    }

    func setFloating(_ floating: Bool) {
        guard self.floating != floating else { return }
        loadViewIfNeeded()
        self.floating = floating
        updatePlacement()
        view.layoutIfNeeded()
    }

    func frameOfControls(in view: UIView) -> CGRect {
        content.view.convert(content.view.bounds, to: view)
    }

    private func updatePlacement() {
        let controls = content.view!
        controls.layer.cornerRadius = floating ? 20 : 0
        NSLayoutConstraint.deactivate(placement)
        if floating {
            let safe = view.safeAreaLayoutGuide
            let width = controls.widthAnchor.constraint(equalTo: safe.widthAnchor, constant: -24)
            let height = controls.heightAnchor.constraint(equalToConstant: 176)
            width.priority = .defaultHigh
            height.priority = .defaultHigh
            placement = [
                controls.centerXAnchor.constraint(equalTo: safe.centerXAnchor),
                controls.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -12),
                controls.widthAnchor.constraint(lessThanOrEqualToConstant: 480),
                controls.widthAnchor.constraint(lessThanOrEqualTo: safe.widthAnchor),
                controls.heightAnchor.constraint(lessThanOrEqualTo: safe.heightAnchor),
                width, height,
            ]
        } else {
            placement = [
                controls.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                controls.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                controls.topAnchor.constraint(equalTo: view.topAnchor),
                controls.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            ]
        }
        NSLayoutConstraint.activate(placement)
    }
}

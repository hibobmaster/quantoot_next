import XCTest

@MainActor
extension XCUIApplication {
    func revealProfileActions() {
        let edit = buttons["Edit profile"].firstMatch
        let actions = [edit, buttons["QR"].firstMatch, buttons["Share"].firstMatch]
        let viewport = descendants(matching: .any)["profile:viewport"].firstMatch
        func fullyVisible() -> Bool {
            guard viewport.exists else { return false }
            let bounds = viewport.frame.insetBy(dx: 0, dy: 4)
            return actions.allSatisfy { action in
                action.exists && action.isHittable &&
                    action.frame.minY >= bounds.minY && action.frame.maxY <= bounds.maxY
            }
        }
        _ = edit.waitForExistence(timeout: 3)
        for _ in 0..<10 {
            if fullyVisible() { return }
            coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
                .press(forDuration: 0.05, thenDragTo: coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.83)),
                       withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        XCTAssertTrue(fullyVisible(), "Reveal the full action row before comparing its layout.")
    }
}

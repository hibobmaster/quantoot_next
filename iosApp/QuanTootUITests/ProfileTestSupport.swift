import XCTest

@MainActor
extension XCUIApplication {
    func revealProfileActions() {
        let edit = buttons["Edit profile"].firstMatch
        _ = edit.waitForExistence(timeout: 3)
        for _ in 0..<10 {
            if edit.exists && edit.isHittable { return }
            coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
                .press(forDuration: 0.05, thenDragTo: coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.83)),
                       withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        XCTAssertTrue(edit.waitForExistence(timeout: 15) && edit.isHittable)
    }
}

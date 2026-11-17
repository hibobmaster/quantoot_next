import XCTest

final class ClientRefinementTests: XCTestCase {
    @MainActor
    func testHomeSelectorUsesBalancedWidth() throws {
        continueAfterFailure = false
        let app = launch()
        let home = topButton("Home", in: app)
        let local = topButton("Local", in: app)
        let federated = topButton("Federated", in: app)
        XCTAssertTrue(federated.waitForExistence(timeout: 15))
        capture("Home selector", app)
        XCTAssertLessThanOrEqual(home.frame.minX, 24)
        XCTAssertGreaterThanOrEqual(federated.frame.maxX, app.frame.width - 24)
        XCTAssertEqual(home.frame.width, local.frame.width, accuracy: 2)
        XCTAssertEqual(local.frame.width, federated.frame.width, accuracy: 2)
        XCTAssertGreaterThanOrEqual(local.frame.height, 44)
    }

    @MainActor
    func testProfileAndNotificationPresentation() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20))
        capture("Profile actions", app)
        app.buttons["Notifications"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Notifications"].firstMatch.waitForExistence(timeout: 10))
        capture("Notification presentation", app)
    }

    @MainActor
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        return app
    }

    @MainActor
    private func topButton(_ label: String, in app: XCUIApplication) -> XCUIElement {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", label))
        XCTAssertTrue(matches.firstMatch.waitForExistence(timeout: 15))
        return matches.allElementsBoundByIndex.min { $0.frame.minY < $1.frame.minY }!
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

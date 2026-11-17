import XCTest

final class ClientNavigationStabilityTests: XCTestCase {
    @MainActor
    func testProfileCategoriesKeepTheVisibleTabRowInPlace() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        let posts = app.buttons["Posts"].firstMatch
        for _ in 0..<8 {
            if posts.exists && posts.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(posts.exists && posts.isHittable)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.6)),
                   withVelocity: .slow, thenHoldForDuration: 0.3)
        XCTAssertTrue(posts.isHittable)
        settle(posts)
        let anchor = posts.frame.midY
        capture("Profile before category change", app)
        for name in ["Media", "Featured", "Activity", "About", "Posts"] {
            let target = app.buttons[name].firstMatch
            if !target.isHittable {
                let row = app.coordinate(withNormalizedOffset: .zero)
                let from = row.withOffset(CGVector(dx: app.frame.width * (name == "Posts" ? 0.2 : 0.85), dy: anchor))
                let to = row.withOffset(CGVector(dx: app.frame.width * (name == "Posts" ? 0.85 : 0.2), dy: anchor))
                from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.2)
            }
            XCTAssertTrue(target.exists && target.isHittable)
            target.tap()
            XCTAssertTrue(target.isSelected)
            settle(target)
            let attachment = XCTAttachment(string: "category=\(name); beforeY=\(anchor); afterY=\(target.frame.midY)")
            attachment.name = "Profile \(name) position"
            attachment.lifetime = .keepAlways
            add(attachment)
            capture("Profile after selecting \(name)", app)
            XCTAssertEqual(target.frame.midY, anchor, accuracy: 4,
                           "Changing the post category must keep the visible profile header and tab row in place.")
        }
    }

    @MainActor
    func testSettingsChildrenUseOneTitleAndOneBackAction() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 15))
        app.buttons["Settings"].tap()
        for label in ["About this server", "Open source licenses", "Privacy policy", "Privacy and reach",
                      "Posting defaults", "Filters", "Notifications"] {
            let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
            for _ in 0..<6 {
                if row.exists && row.isHittable { break }
                app.swipeUp()
            }
            XCTAssertTrue(row.exists && row.isHittable)
            row.tap()
            capture("Settings child \(label)", app)
            XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "‹ Settings")).firstMatch.exists,
                           "A child page must use the app bar instead of a second Settings back label.")
            let title = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
            XCTAssertTrue(title.waitForExistence(timeout: 10))
            XCTAssertLessThan(title.frame.minY, app.frame.height * 0.2)
            app.buttons["Close"].firstMatch.tap()
            let language = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Language")).firstMatch
            XCTAssertTrue(language.waitForExistence(timeout: 10), "One back action must return to the settings index.")
        }
        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 10))
    }

    @MainActor
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        return app
    }

    @MainActor
    private func settle(_ element: XCUIElement) {
        var frame = CGRect.null
        var since = Date()
        let stable = NSPredicate { _, _ in
            guard element.exists else { return false }
            if element.frame != frame {
                frame = element.frame
                since = Date()
                return false
            }
            return Date().timeIntervalSince(since) >= 0.6
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: stable, object: nil)], timeout: 5), .completed)
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

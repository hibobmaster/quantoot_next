import XCTest

final class ClientNavigationStabilityTests: XCTestCase {
    @MainActor
    func testEmptyMediaStillAllowsTheProfileHeaderToScroll() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        let media = app.buttons["Media"].firstMatch
        XCTAssertTrue(media.exists && media.isHittable)
        media.tap()
        XCTAssertTrue(app.staticTexts["No content yet"].waitForExistence(timeout: 15),
                      "The existing test account must exercise the actual empty-media case.")
        settle(media)
        let before = media.frame.midY
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.58)),
                   withVelocity: .slow, thenHoldForDuration: 0.3)
        settle(media)
        let attachment = XCTAttachment(string: "emptyMediaBeforeY=\(before); afterDragY=\(media.frame.midY)")
        attachment.name = "Empty Media scroll position"
        attachment.lifetime = .keepAlways
        add(attachment)
        capture("Empty Media after scrolling header", app)
        XCTAssertLessThan(media.frame.midY, before - 40,
                          "An empty category must have enough vertical reading extent to move the shared header.")
    }

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
    func testActivityFiltersAndRepeatedCategoryChangesKeepTheHeaderStable() throws {
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
        settle(posts)
        let anchor = posts.frame.midY
        let activity = app.buttons["Activity"].firstMatch
        XCTAssertTrue(activity.exists && activity.isHittable)
        activity.tap()
        settle(activity)
        XCTAssertEqual(activity.frame.midY, anchor, accuracy: 4)
        for label in ["Show replies", "Show boosts", "Show replies"] {
            let filter = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
            XCTAssertTrue(filter.waitForExistence(timeout: 10))
            XCTAssertTrue(filter.isHittable)
            filter.tap()
            settle(activity)
            XCTAssertEqual(activity.frame.midY, anchor, accuracy: 4)
        }
        posts.tap()
        settle(posts)
        for label in ["Media", "Posts", "Media", "Posts"] {
            let category = app.buttons["profile:category:\(label)"].firstMatch
            XCTAssertTrue(category.exists && category.isHittable)
            category.tap()
            let selected = NSPredicate { _, _ in category.exists && category.isSelected }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: selected, object: nil)], timeout: 3), .completed)
            XCTAssertEqual(category.frame.midY, anchor, accuracy: 4)
        }
        settle(posts)
        XCTAssertEqual(posts.frame.midY, anchor, accuracy: 4)
        capture("Profile after Activity filters and repeated changes", app)
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
    func testLocalizedSettingsChildReturnsThroughAccounts() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "zh"]
        app.launch()
        XCTAssertTrue(app.buttons["设置"].waitForExistence(timeout: 15))
        app.buttons["设置"].tap()
        let server = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "关于此服务器")).firstMatch
        for _ in 0..<6 {
            if server.exists && server.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(server.exists && server.isHittable)
        server.tap()
        let title = app.staticTexts["关于此服务器"].firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertLessThan(title.frame.minY, app.frame.height * 0.2)
        XCTAssertFalse(app.buttons["设置"].exists)
        app.buttons["账号"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["账号"].firstMatch.waitForExistence(timeout: 10))
        app.buttons["关闭"].firstMatch.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertLessThan(title.frame.minY, app.frame.height * 0.2)
        capture("Localized Settings child after Accounts", app)
        app.buttons["关闭"].firstMatch.tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "语言")).firstMatch.waitForExistence(timeout: 10))
        app.buttons["关闭"].firstMatch.tap()
        XCTAssertTrue(app.buttons["个人资料"].firstMatch.waitForExistence(timeout: 10))
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

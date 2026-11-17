import XCTest

final class ClientInteractionTests: XCTestCase {
    @MainActor
    func testProfileHeaderScrollsAwayToRevealPosts() throws {
        continueAfterFailure = false
        let app = profile()
        let posts = app.buttons["Posts"].firstMatch
        XCTAssertTrue(posts.waitForExistence(timeout: 15))
        let before = posts.frame.minY
        capture("Profile before feed scroll", app)
        drag(app, from: CGVector(dx: 0.5, dy: 0.78), to: CGVector(dx: 0.5, dy: 0.27))
        let moved = NSPredicate { _, _ in posts.exists && posts.frame.minY < before - 100 }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: moved, object: nil)], timeout: 5), .completed,
                       "Scrolling the posts must move the profile header out of the reading area.")
        capture("Profile header collapsed into feed", app)
        XCTAssertLessThan(posts.frame.maxY, app.frame.height * 0.4)
        XCTAssertFalse(app.buttons["Edit profile"].firstMatch.isHittable)
    }

    @MainActor
    func testProfilePrimaryAndSharingActionsUseOneRow() throws {
        continueAfterFailure = false
        let app = profile()
        let edit = app.buttons["Edit profile"].firstMatch
        let qr = app.buttons["QR"].firstMatch
        let share = app.buttons["Share"].firstMatch
        XCTAssertTrue(qr.waitForExistence(timeout: 10))
        XCTAssertTrue(share.exists)
        capture("Profile action row", app)
        XCTAssertEqual(edit.frame.midY, qr.frame.midY, accuracy: 2)
        XCTAssertEqual(qr.frame.midY, share.frame.midY, accuracy: 2)
        XCTAssertGreaterThanOrEqual(qr.frame.width, 44)
        XCTAssertGreaterThanOrEqual(qr.frame.height, 44)
        XCTAssertLessThanOrEqual(qr.frame.width, 64)
    }

    @MainActor
    func testReturningFromFederatedPostKeepsSelectedTab() throws {
        continueAfterFailure = false
        let app = launch()
        let federated = app.buttons["Federated"].firstMatch
        XCTAssertTrue(federated.waitForExistence(timeout: 15))
        federated.tap()
        XCTAssertTrue(federated.isSelected)
        capture("Federated before opening post", app)
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies"))
        XCTAssertTrue(replies.firstMatch.waitForExistence(timeout: 25))
        var visible = replies.allElementsBoundByIndex.first { $0.isHittable }
        for _ in 0..<5 where visible == nil {
            drag(app, from: CGVector(dx: 0.5, dy: 0.76), to: CGVector(dx: 0.5, dy: 0.4))
            visible = replies.allElementsBoundByIndex.first { $0.isHittable }
        }
        try XCTUnwrap(visible).tap()
        let heading = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Replies")).firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 20))
        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(federated.waitForExistence(timeout: 15))
        capture("Federated after returning", app)
        XCTAssertTrue(federated.isSelected, "Returning must preserve the source selected before opening the post.")
    }

    @MainActor
    func testImageDismissesWithDownwardSwipe() throws {
        try imageDismisses(from: CGVector(dx: 0.5, dy: 0.38), to: CGVector(dx: 0.5, dy: 0.76))
    }

    @MainActor
    func testImageDismissesWithUpwardSwipe() throws {
        try imageDismisses(from: CGVector(dx: 0.5, dy: 0.72), to: CGVector(dx: 0.5, dy: 0.3))
    }

    @MainActor
    func testZoomedPanAndHorizontalPagingRemainAvailable() throws {
        continueAfterFailure = false
        let app = gallery()
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Zoom in")).firstMatch.tap()
        drag(app, from: CGVector(dx: 0.5, dy: 0.38), to: CGVector(dx: 0.5, dy: 0.76))
        XCTAssertTrue(app.staticTexts["1 of 2"].exists, "A zoomed image must pan instead of dismissing.")
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Reset zoom")).firstMatch.tap()
        drag(app, from: CGVector(dx: 0.84, dy: 0.5), to: CGVector(dx: 0.16, dy: 0.5))
        XCTAssertTrue(app.staticTexts["2 of 2"].waitForExistence(timeout: 10))
        capture("Gallery paging after zoomed pan", app)
    }

    @MainActor
    private func imageDismisses(from: CGVector, to: CGVector) throws {
        continueAfterFailure = false
        let app = gallery()
        drag(app, from: from, to: to)
        let dismissed = NSPredicate { _, _ in !app.staticTexts["1 of 2"].exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: dismissed, object: nil)], timeout: 5), .completed)
        XCTAssertTrue(app.buttons["Explore"].firstMatch.exists)
        capture("Gallery dismissed by vertical swipe", app)
    }

    @MainActor
    private func profile() -> XCUIApplication {
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20))
        return app
    }

    @MainActor
    private func gallery() -> XCUIApplication {
        let app = launch()
        XCTAssertTrue(app.buttons["Explore"].waitForExistence(timeout: 15))
        app.buttons["Explore"].tap()
        let search = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Search ")).firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        search.typeText("https://fairy.id/@zuola/117630161048006375")
        let submit = app.buttons.matching(NSPredicate(format: "label == %@", "Explore"))
            .allElementsBoundByIndex.min { $0.frame.minY < $1.frame.minY }!
        let ready = NSPredicate { _, _ in submit.isEnabled }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: nil)], timeout: 15), .completed)
        submit.tap()
        let preview = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "media-preview-")).firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 25))
        preview.tap()
        XCTAssertTrue(app.staticTexts["1 of 2"].waitForExistence(timeout: 15))
        return app
    }

    @MainActor
    private func drag(_ app: XCUIApplication, from: CGVector, to: CGVector) {
        app.coordinate(withNormalizedOffset: from)
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: to),
                   withVelocity: .slow, thenHoldForDuration: 0.3)
    }

    @MainActor
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        return app
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

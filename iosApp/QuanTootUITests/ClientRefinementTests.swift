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
        XCTAssertGreaterThanOrEqual(local.frame.height, 44, "Timeline tab frame \(local.frame)")
    }

    @MainActor
    func testLargeTextSelectorKeepsLabelsOnOneLine() throws {
        continueAfterFailure = false
        let app = launch()
        let home = topButton("Home", in: app)
        let local = topButton("Local", in: app)
        let federated = topButton("Federated", in: app)
        XCTAssertTrue(federated.waitForExistence(timeout: 15))
        let homeLabel = home.staticTexts["Home"]
        let localLabel = local.staticTexts["Local"]
        let federatedLabel = federated.staticTexts["Federated"]
        XCTAssertTrue(homeLabel.exists && localLabel.exists && federatedLabel.exists)
        XCTAssertEqual(homeLabel.frame.height, localLabel.frame.height, accuracy: 2)
        XCTAssertEqual(localLabel.frame.height, federatedLabel.frame.height, accuracy: 2)
        XCTAssertGreaterThanOrEqual(federated.frame.height, 44)
        federated.tap()
        XCTAssertTrue(federated.isSelected)
        app.buttons["Profile"].firstMatch.tap()
        app.buttons["Home"].firstMatch.tap()
        XCTAssertTrue(federated.waitForExistence(timeout: 5))
        XCTAssertTrue(federated.isSelected && federated.isHittable)
        capture("Large text Home selector", app)
    }

    @MainActor
    func testProfileAndNotificationPresentation() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20))
        capture("Profile actions", app)
        app.buttons["Notifications"].firstMatch.tap()
        XCTAssertTrue(app.screenHeading("Notifications").waitForExistence(timeout: 10))
        capture("Notification presentation", app)
    }

    @MainActor
    func testQuotedPostHasInsetSurface() throws {
        continueAfterFailure = false
        let app = launch()
        search("https://showcase.meows.lol/notes/atnw57bswt", in: app)
        let outer = app.descendants(matching: .any)["status-card-117630425003795241"]
        let quoted = app.descendants(matching: .any)["status-card-117630401283871563"]
        XCTAssertTrue(outer.waitForExistence(timeout: 25))
        for _ in 0..<6 {
            if quoted.exists && quoted.frame.minY < app.frame.height * 0.45 { break }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.38)),
                       withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        XCTAssertTrue(quoted.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(quoted.frame.width, outer.frame.width - 16)
        XCTAssertGreaterThanOrEqual(quoted.frame.minX, outer.frame.minX + 8)
        capture("Nested quote hierarchy", app)
    }

    @MainActor
    func testAuthorizedPostEditDeleteRoundTrip() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["QUANTOOT_LIVE_TESTS"] == "1",
                          "Real-account acceptance requires explicit opt-in.")
        continueAfterFailure = false
        let existing = ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_MARKER"]
        let nonce = existing ?? UUID().uuidString.lowercased()
        XCTAssertNotNil(UUID(uuidString: nonce))
        let original = "QuanToot acceptance \(nonce) version1"
        let edited = "QuanToot acceptance \(nonce) version2"
        record(existing == nil ? "Created test marker" : "Resumed test marker", nonce)
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20))
        let content = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Content")).firstMatch
        if existing == nil {
            app.buttons["New post"].tap()
            XCTAssertTrue(content.waitForExistence(timeout: 10))
            content.tap()
            content.typeText(original)
            XCTAssertTrue((content.value as? String)?.contains(nonce) == true)
            XCTAssertFalse((content.value as? String)?.contains("@") == true)
            let visibility = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Visibility")).firstMatch
            reveal(visibility, in: app)
            visibility.tap()
            let direct = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", "Private mention")).firstMatch
            XCTAssertTrue(direct.waitForExistence(timeout: 5))
            direct.tap()
            XCTAssertTrue(visibility.label.contains("Private mention"))
            capture("Self-only draft before publish", app)
            let publish = app.buttons["Publish"]
            reveal(publish, in: app)
            XCTAssertTrue(publish.isEnabled)
            settledTap(publish)
        }
        if let existingURL = ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_URL"] {
            search(existingURL, in: app)
            let recovered = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", nonce)).firstMatch
            XCTAssertTrue(recovered.waitForExistence(timeout: 25))
            recovered.tap()
        } else {
            openMarkedPost(nonce, in: app)
        }
        let card = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "status-card-")).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 15))
        let statusID = String(card.identifier.dropFirst("status-card-".count))
        XCTAssertFalse(statusID.isEmpty)
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Private mention")).firstMatch.exists)
        let handle = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "@")).firstMatch.label
        let account = String(handle.dropFirst()).split(separator: "@").first.map(String.init) ?? ""
        XCTAssertFalse(account.isEmpty)
        let url = "https://m.qqs.tw/@\(account)/\(statusID)"
        record("Published self-only post", "marker=\(nonce); statusID=\(statusID); url=\(url); visibility=direct")
        capture("Published self-only post", app)

        app.buttons["More post actions"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Edit"].waitForExistence(timeout: 5))
        app.buttons["Edit"].tap()
        XCTAssertTrue(content.waitForExistence(timeout: 10))
        let previous = content.value as? String ?? ""
        content.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.95)).tap()
        content.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.count))
        content.typeText(edited)
        let immutableVisibility = app.staticTexts["Visibility cannot be changed after publishing."]
        reveal(immutableVisibility, in: app)
        XCTAssertTrue(immutableVisibility.exists)
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Visibility")).firstMatch.exists)
        let quotePolicy = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Who can quote")).firstMatch
        reveal(quotePolicy, in: app)
        quotePolicy.tap()
        let keepPolicy = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Keep current setting"))
        XCTAssertTrue(keepPolicy.firstMatch.waitForExistence(timeout: 5))
        keepPolicy.allElementsBoundByIndex.first { $0.isHittable }?.tap()
        let save = app.buttons["Save changes"]
        reveal(save, in: app)
        capture("Ready to submit edit", app)
        settledTap(save)
        let updated = markedText(nonce, version: "version2", in: app)
        if !updated.waitForExistence(timeout: 8) {
            for _ in 0..<6 {
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.22))
                    .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.48)))
            }
            record("Edit response hierarchy", app.debugDescription)
            capture("Edit response", app)
        }
        XCTAssertTrue(updated.exists)

        app.terminate()
        app.launch()
        search(url, in: app)
        let readback = markedText(nonce, version: "version2", in: app)
        XCTAssertTrue(readback.waitForExistence(timeout: 25))
        readback.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Private mention")).firstMatch.waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any)["status-card-\(statusID)"].exists)
        record("Edited post read back", "statusID=\(statusID); version=version2; freshProcess=true")
        capture("Edited post after server read", app)

        app.buttons["More post actions"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Delete"].waitForExistence(timeout: 5))
        app.buttons["Delete"].tap()
        let confirmation = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Delete post?")).firstMatch
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label == %@", "Delete"))
            .allElementsBoundByIndex.last { $0.isHittable }?.tap()
        let deletedMarker = markedText(nonce, version: "version2", in: app)
        let removed = NSPredicate { _, _ in !deletedMarker.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: removed, object: nil)], timeout: 20), .completed)
        app.terminate()
        app.launch()
        search(url, in: app)
        XCTAssertTrue(app.staticTexts["No content yet"].waitForExistence(timeout: 25))
        XCTAssertFalse(markedText(nonce, version: "version2", in: app).exists)
        record("Deleted post not rendered after search", "statusID=\(statusID); freshProcess=true; freshSearchEmpty=true; requiresIndependentServerRead=true")
        capture("Deleted test post after server read", app)
    }

    @MainActor
    func testAuthorizedImageSaveShowsFeedbackInsideViewer() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["QUANTOOT_LIVE_TESTS"] == "1",
                          "Photos acceptance requires explicit opt-in.")
        continueAfterFailure = false
        let app = launch()
        search("https://fairy.id/@zuola/117630161048006375", in: app)
        let preview = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "media-preview-")).firstMatch
        if !preview.waitForExistence(timeout: 15) { app.swipeUp() }
        XCTAssertTrue(preview.waitForExistence(timeout: 15))
        record("Saved attachment", preview.identifier)
        preview.tap()
        XCTAssertTrue(app.staticTexts["1 of 2"].waitForExistence(timeout: 15))
        app.buttons["Save to gallery"].tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let alert = springboard.alerts.firstMatch
        if alert.waitForExistence(timeout: 3) {
            record("Photos permission choices", alert.buttons.allElementsBoundByIndex.map(\.label).joined(separator: " | "))
            let allow = ["Allow Photos to Be Added", "Allow Adding Photos", "Allow Full Access", "Allow Access to All Photos", "Allow"]
                .map { alert.buttons[$0] }.first { $0.exists && $0.isHittable }
            XCTAssertNotNil(allow)
            allow?.tap()
        }
        let saved = app.staticTexts["Image saved to gallery"].firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 25))
        XCTAssertTrue(app.staticTexts["1 of 2"].exists)
        capture("Image saved inside gallery", app)
    }

    @MainActor
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 {
            if element.exists && element.isHittable { return }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.42))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)),
                       withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        XCTAssertTrue(element.exists && element.isHittable)
    }

    @MainActor
    private func settledTap(_ element: XCUIElement) {
        var previous = CGRect.null
        var since = Date()
        let stable = NSPredicate { _, _ in
            guard element.exists && element.isHittable else { return false }
            let frame = element.frame
            if frame != previous {
                previous = frame
                since = Date()
                return false
            }
            return Date().timeIntervalSince(since) >= 0.4
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: stable, object: nil)], timeout: 5), .completed)
        element.tap()
    }

    @MainActor
    private func openMarkedPost(_ marker: String, in app: XCUIApplication) {
        let activity = app.buttons["Activity"].firstMatch
        if activity.exists && activity.isHittable {
            activity.tap()
            let replies = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", "Show replies")).firstMatch
            XCTAssertTrue(replies.waitForExistence(timeout: 10))
            if !replies.isSelected { replies.tap() }
        }
        let text = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", marker)).firstMatch
        for _ in 0..<16 {
            if text.exists && text.isHittable {
                text.tap()
                return
            }
            let arrivals = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "New posts")).firstMatch
            if arrivals.exists && arrivals.isHittable { arrivals.tap() }
            else {
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.84))
                    .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62)))
            }
        }
        record("Unreachable test post hierarchy", app.debugDescription)
        capture("Unreachable test post", app)
        XCTFail("The exact newly created test post must be reachable.")
    }

    @MainActor
    private func markedText(_ marker: String, version: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", marker, version)).firstMatch
    }

    @MainActor
    private func search(_ query: String, in app: XCUIApplication) {
        XCTAssertTrue(app.buttons["Explore"].waitForExistence(timeout: 15))
        app.buttons["Explore"].tap()
        let field = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Search ")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(query)
        let submit = topButton("Explore", in: app)
        let enabled = NSPredicate { _, _ in submit.isEnabled }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: nil)], timeout: 15), .completed)
        submit.tap()
    }

    @MainActor
    private func record(_ name: String, _ value: String) {
        let attachment = XCTAttachment(string: value)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
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

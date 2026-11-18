import XCTest

final class ClientExperienceTests: XCTestCase {
    @MainActor
    func testSelectionSheetAndBlankDefaultServerPreserveTheSession() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()

        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 15))
        app.buttons["Settings"].tap()
        let language = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Language")).firstMatch
        XCTAssertTrue(language.waitForExistence(timeout: 5))
        language.tap()
        let english = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "English")).firstMatch
        XCTAssertTrue(english.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["简体中文"].exists)
        XCTAssertTrue(app.staticTexts["繁體中文"].exists)
        capture("Language selection sheet", app)
        english.tap()
        app.buttons["Close"].firstMatch.tap()

        app.buttons["Accounts"].tap()
        app.buttons["Add account"].tap()
        let field = app.textViews
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Instance domain")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        let previous = field.value as? String ?? ""
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.count))
        XCTAssertEqual(field.value as? String, "")
        XCTAssertTrue(app.buttons["Continue to Log In"].isEnabled)
        XCTAssertTrue(app.buttons["Browse Public Posts"].isEnabled)
        XCTAssertTrue(app.staticTexts["Leave blank to use m.qqs.tw."].exists)
        capture("Blank default instance", app)
        app.buttons["Close"].firstMatch.tap()
        app.buttons["Close"].firstMatch.tap()
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20))
    }

    @MainActor
    func testLoggedInReaderCanOpenRepliesWithoutARefreshButton() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()

        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20),
                      "The existing test-account session must remain signed in.")
        app.buttons["Home"].firstMatch.tap()

        XCTAssertFalse(app.buttons["Refresh"].exists,
                       "Timeline refresh belongs to the pull gesture.")
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies"))
        XCTAssertTrue(replies.firstMatch.waitForExistence(timeout: 20))
        let visibleReply = try XCTUnwrap(replies.allElementsBoundByIndex.first { $0.isHittable })
        visibleReply.tap()
        let heading = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Replies")).firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 20))
        capture("Conversation and replies", app)
        XCTAssertFalse(app.textViews["Content"].exists,
                       "Opening replies must show the conversation rather than a composer.")
    }

    @MainActor
    func testPublicConversationShowsItsExistingReply() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        let query = "https://pari.cafe/notes/atntpq6r0z3x18hr"
        search(query, in: app)
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies")).firstMatch
        XCTAssertTrue(replies.waitForExistence(timeout: 25))
        replies.tap()
        let author = app.staticTexts["@seeUtomorrow@pari.cafe"].firstMatch
        if !author.waitForExistence(timeout: 5) { app.swipeUp() }
        XCTAssertTrue(author.waitForExistence(timeout: 20))
        capture("Existing public conversation reply", app)
        app.buttons["Close"].firstMatch.tap()
        let field = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Search ")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, query)
        XCTAssertTrue(replies.isHittable)
        app.buttons["Clear search"].firstMatch.tap()
        XCTAssertFalse(app.buttons["Clear search"].firstMatch.exists)
    }

    @MainActor
    private func search(_ query: String, in app: XCUIApplication) {
        XCTAssertTrue(app.buttons["Explore"].waitForExistence(timeout: 15))
        app.buttons["Explore"].tap()
        let field = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Search ")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(query)
        let submit = app.buttons.matching(NSPredicate(format: "label == %@", "Explore"))
            .allElementsBoundByIndex.min { $0.frame.minY < $1.frame.minY }
        XCTAssertNotNil(submit)
        let enabled = NSPredicate { _, _ in submit?.isEnabled == true }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: nil)], timeout: 15), .completed)
        submit?.tap()
    }

    @MainActor
    func testComposerOffersLanguageAndEmojiWithoutPublishing() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        XCTAssertTrue(app.buttons["New post"].waitForExistence(timeout: 15))
        app.buttons["New post"].tap()
        let publish = app.buttons["Publish"].firstMatch
        XCTAssertTrue(publish.waitForExistence(timeout: 5))
        XCTAssertFalse(publish.isEnabled)
        capture("Empty writing canvas", app)
        let emoji = app.buttons["Emoji"]
        XCTAssertTrue(emoji.waitForExistence(timeout: 5))
        emoji.tap()
        XCTAssertTrue(app.buttons["✨"].waitForExistence(timeout: 10))
        app.buttons["✨"].tap()
        capture("Composer emoji picker", app)
        app.buttons.matching(NSPredicate(format: "label == %@", "Close")).allElementsBoundByIndex.last { $0.isHittable }?.tap()
        app.swipeUp()
        let language = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Post language")).firstMatch
        XCTAssertTrue(language.waitForExistence(timeout: 5))
        language.tap()
        let defaultOption = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Default")).firstMatch
        XCTAssertTrue(defaultOption.waitForExistence(timeout: 5))
        capture("Post language choices", app)
        defaultOption.tap()
        XCTAssertTrue(publish.isEnabled && publish.isHittable)
        capture("Writing canvas with an unsent draft", app)
        app.buttons["Close"].firstMatch.tap()
        let discard = app.buttons["Discard"]
        XCTAssertTrue(discard.waitForExistence(timeout: 5))
        discard.tap()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 5))
    }

    @MainActor
    func testGalleryPreservesBothAttachmentsAndZoomControls() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://fairy.id/@zuola/117630161048006375", in: app)
        let preview = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "media-preview-")).firstMatch
        if !preview.waitForExistence(timeout: 15) { app.swipeUp() }
        XCTAssertTrue(preview.waitForExistence(timeout: 15))
        XCTAssertGreaterThan(preview.frame.width, app.frame.width * 0.35)
        capture("Filled media previews", app)
        preview.tap()
        XCTAssertTrue(app.staticTexts["1 of 2"].waitForExistence(timeout: 15))
        app.buttons["Next attachment"].tap()
        XCTAssertTrue(app.staticTexts["2 of 2"].waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Zoom in")).firstMatch.tap()
        let reset = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Reset zoom")).firstMatch
        XCTAssertTrue(reset.label.contains("150%"))
        reset.tap()
        capture("Paged original media gallery", app)
        app.buttons.matching(NSPredicate(format: "label == %@", "Close")).allElementsBoundByIndex.last { $0.isHittable }?.tap()
    }

    @MainActor
    func testServerRulesAndContactAreReachable() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 15))
        app.buttons["Settings"].tap()
        let about = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "About this server")).firstMatch
        if !about.isHittable { app.swipeUp() }
        XCTAssertTrue(about.waitForExistence(timeout: 5))
        about.tap()
        let rules = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Server rules")).firstMatch
        XCTAssertTrue(rules.waitForExistence(timeout: 20))
        rules.tap()
        capture("Server rules", app)
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Contact")).firstMatch.tap()
        capture("Server contact", app)
    }

    @MainActor
    func testPublicTimelineSupportsPullRefreshAndLiveArrivals() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        XCTAssertTrue(app.buttons["Federated"].waitForExistence(timeout: 15))
        let entered = Date()
        app.buttons["Federated"].tap()
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies")).firstMatch
        XCTAssertTrue(replies.waitForExistence(timeout: 20))
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
        start.press(forDuration: 0.1, thenDragTo: end)
        XCTAssertFalse(app.buttons["Refresh"].exists)
        XCTAssertTrue(replies.waitForExistence(timeout: 15))
        capture("Public timeline after pull refresh", app)
        app.swipeUp()
        let banner = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "New posts")).firstMatch
        let arrival = banner.waitForExistence(timeout: 8)
        let elapsed = Date().timeIntervalSince(entered)
        capture("Public timeline while reading", app)
        let observation = XCTAttachment(string: "newPostBannerObserved=\(arrival); elapsedSinceFeedEntry=\(elapsed); scheduledRestInterval=20")
        observation.name = "Live arrival observation"
        observation.lifetime = .keepAlways
        add(observation)
        if arrival {
            capture("Live new post banner", app)
            banner.tap()
            XCTAssertTrue(replies.waitForExistence(timeout: 5))
        }
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

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
        app.buttons["Close"].firstMatch.tap()
        app.buttons["Close"].firstMatch.tap()
        app.buttons["Profile"].firstMatch.tap()
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
        XCTAssertTrue(app.buttons["Edit profile"].firstMatch.waitForExistence(timeout: 20),
                      "The existing test-account session must remain signed in.")
        app.buttons["Home"].firstMatch.tap()

        XCTAssertFalse(app.buttons["Refresh"].exists,
                       "Timeline refresh belongs to the pull gesture.")
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies")).firstMatch
        XCTAssertTrue(replies.waitForExistence(timeout: 20))
        replies.tap()
        XCTAssertTrue(app.staticTexts["Replies"].firstMatch.waitForExistence(timeout: 20))
        XCTAssertFalse(app.textViews["Content"].exists,
                       "Opening replies must show the conversation rather than a composer.")
    }
}

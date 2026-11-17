import XCTest

final class ClientExperienceTests: XCTestCase {
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

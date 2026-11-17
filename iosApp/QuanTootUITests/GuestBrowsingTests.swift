import XCTest

final class GuestBrowsingTests: XCTestCase {
    @MainActor
    func testGuestCanChooseInstanceAndReadPublicPosts() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        let field = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Instance domain")).firstMatch
        guard field.waitForExistence(timeout: 10) else {
            throw XCTSkip("Run the guest setup test on a simulator without an existing instance.")
        }
        field.tap()
        field.typeText("m.qqs.tw")
        let browse = app.buttons["Browse Public Posts"]
        XCTAssertTrue(browse.isEnabled)
        browse.tap()
        XCTAssertTrue(app.staticTexts["Home"].firstMatch.waitForExistence(timeout: 10))
        let account = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "@")).firstMatch
        XCTAssertTrue(account.waitForExistence(timeout: 25))
    }
}

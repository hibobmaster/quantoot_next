import XCTest

final class GuestBrowsingTests: XCTestCase {
    @MainActor
    func testGuestSettingsChildrenHaveOneTitleAndReturnToIndex() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        let browse = app.buttons["Browse Public Posts"]
        guard browse.waitForExistence(timeout: 10) else {
            throw XCTSkip("Run the guest Settings test on an isolated simulator without a selected instance.")
        }
        browse.tap()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 15))
        app.buttons["Settings"].tap()
        for label in ["Privacy and reach", "Posting defaults", "Filters"] {
            XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch.exists)
        }
        for label in ["About this server", "Privacy policy", "Open source licenses"] {
            let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
            for _ in 0..<6 {
                if row.exists && row.isHittable { break }
                app.swipeUp()
            }
            XCTAssertTrue(row.exists && row.isHittable)
            row.tap()
            let title = app.staticTexts[label].firstMatch
            XCTAssertTrue(title.waitForExistence(timeout: 10))
            XCTAssertLessThan(title.frame.minY, app.frame.height * 0.2)
            XCTAssertFalse(app.buttons["Settings"].exists)
            XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "‹ Settings")).firstMatch.exists)
            app.buttons["Close"].firstMatch.tap()
            XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Language")).firstMatch.waitForExistence(timeout: 10))
        }
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Guest settings index after child returns"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

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

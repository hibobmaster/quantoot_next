import XCTest
import CoreGraphics

final class ClientVisualPolishTests: XCTestCase {
    @MainActor
    func testThreadFocusSettlesToNeutralSurface() throws {
        continueAfterFailure = false
        let app = launch()
        let replies = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies"))
        XCTAssertTrue(replies.firstMatch.waitForExistence(timeout: 20))
        let reply = try XCTUnwrap(replies.allElementsBoundByIndex.first { $0.isHittable })
        let cards = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "status-card-"))
        let source = try XCTUnwrap(cards.allElementsBoundByIndex.first { $0.frame.contains(reply.frame.origin) })
        let identifier = source.identifier
        reply.tap()
        XCTAssertTrue(app.buttons["Reply"].firstMatch.waitForExistence(timeout: 20))
        let card = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 15))
        let entered = Date()
        let settled = NSPredicate { _, _ in Date().timeIntervalSince(entered) >= 3 }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: settled, object: nil)], timeout: 5), .completed)
        let visible = card.frame.intersection(CGRect(x: 0, y: app.frame.height * 0.18,
                                                   width: app.frame.width, height: app.frame.height * 0.6))
        XCTAssertGreaterThan(visible.height, 30)
        let screenshot = app.screenshot()
        capture("Settled thread surface", screenshot)
        let rgb = try pixel(CGPoint(x: card.frame.minX + 4, y: visible.midY), in: screenshot)
        let spread = Int(rgb.max()!) - Int(rgb.min()!)
        let sample = XCTAttachment(string: "card=\(identifier); rgb=\(rgb); channelSpread=\(spread)")
        sample.name = "Thread surface pixel"
        sample.lifetime = .keepAlways
        add(sample)
        XCTAssertLessThanOrEqual(spread, 12, "The post must settle to the neutral reading surface instead of remaining green.")
    }

    @MainActor
    func testProfilePostsStartsAtLeadingEdge() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 15))
        app.buttons["Profile"].firstMatch.tap()
        app.revealProfileActions()
        let posts = app.buttons["Posts"].firstMatch
        for _ in 0..<8 {
            if posts.exists && posts.isHittable { break }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                       withVelocity: .slow, thenHoldForDuration: 0.2)
        }
        XCTAssertTrue(posts.exists && posts.isHittable)
        capture("Profile tabs initial alignment", app.screenshot())
        XCTAssertTrue(posts.isSelected)
        XCTAssertLessThanOrEqual(posts.frame.minX - app.frame.minX, 16,
                                 "Posts must start at the leading edge without a horizontal correction gesture.")
        app.buttons["Media"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Media"].firstMatch.isSelected)
        posts.tap()
        XCTAssertTrue(posts.isSelected)
    }

    @MainActor
    func testLicensesListOnlyBundledDependencies() throws {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 15))
        app.buttons["Settings"].tap()
        let licenses = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Open source licenses")).firstMatch
        for _ in 0..<5 {
            if licenses.exists && licenses.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(licenses.exists && licenses.isHittable)
        licenses.tap()
        XCTAssertTrue(app.buttons["Kotlin · Apache 2.0"].waitForExistence(timeout: 5))
        capture("Current dependency licenses", app.screenshot())
        XCTAssertTrue(app.buttons["Compose Multiplatform · Apache 2.0"].exists)
        XCTAssertTrue(app.buttons["Ktor · Apache 2.0"].exists)
        XCTAssertTrue(app.buttons["Coil · Apache 2.0"].exists)
        XCTAssertTrue(app.buttons["QRose · MIT"].exists)
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "WenKai")).firstMatch.exists)
        XCTAssertFalse(app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "WenKai")).firstMatch.exists)
    }

    @MainActor
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        return app
    }

    private func pixel(_ point: CGPoint, in screenshot: XCUIScreenshot) throws -> [UInt8] {
        let image = screenshot.image
        let cgImage = try XCTUnwrap(image.cgImage)
        let scale = CGFloat(cgImage.width) / image.size.width
        let crop = try XCTUnwrap(cgImage.cropping(to: CGRect(x: floor(point.x * scale),
                                                           y: floor(point.y * scale), width: 1, height: 1)))
        var rgba = [UInt8](repeating: 0, count: 4)
        try rgba.withUnsafeMutableBytes { bytes in
            let context = try XCTUnwrap(CGContext(data: bytes.baseAddress, width: 1, height: 1,
                                                  bitsPerComponent: 8, bytesPerRow: 4,
                                                  space: CGColorSpaceCreateDeviceRGB(),
                                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(crop, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        return Array(rgba.prefix(3))
    }

    private func capture(_ name: String, _ screenshot: XCUIScreenshot) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

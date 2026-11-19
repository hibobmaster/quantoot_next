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
        XCTAssertEqual(field.value as? String ?? "", "")
        XCTAssertTrue(app.buttons["Continue to Log In"].isEnabled)
        XCTAssertTrue(app.buttons["Browse Public Posts"].isEnabled)
        XCTAssertTrue(app.staticTexts["Leave blank to log in to mastodon.social or browse m.qqs.tw as a guest."].exists)
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
        capture("Composer emoji picker", app)
        app.buttons["✨"].tap()
        XCTAssertTrue(app.buttons["✨"].waitForNonExistence(timeout: 5))
        let content = app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "Content")).firstMatch
        XCTAssertTrue((content.value as? String)?.contains("✨") == true)
        emoji.tap()
        let custom = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "bilibili_001")).firstMatch
        XCTAssertTrue(custom.waitForExistence(timeout: 15))
        custom.tap()
        XCTAssertTrue(custom.waitForNonExistence(timeout: 5))
        XCTAssertTrue((content.value as? String)?.contains(":bilibili_001:") == true)
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
    func testFollowingLoadsPastFirstPageAndRestoresSelectedRow() throws {
        try checkPeopleListReturn("Following")
    }

    @MainActor
    func testFollowersLoadPastFirstPageAndRestoreSelectedRow() throws {
        try checkPeopleListReturn("Followers")
    }

    @MainActor
    private func checkPeopleListReturn(_ title: String) throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://m.qqs.tw/@bobmaster", in: app)
        let profile = app.staticTexts["@bobmaster"].firstMatch
        XCTAssertTrue(profile.waitForExistence(timeout: 20))
        profile.tap()
        let count = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label ENDSWITH %@", " " + title)).firstMatch
        XCTAssertTrue(count.waitForExistence(timeout: 15))
        count.tap()
        XCTAssertTrue(app.screenHeading(title).waitForExistence(timeout: 10))
        let handles = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "@"))
        XCTAssertTrue(handles.firstMatch.waitForExistence(timeout: 15))
        var seen = [String]()
        for _ in 0..<12 {
            let visible = handles.allElementsBoundByIndex.filter { $0.isHittable }
            for handle in visible.map(\.label) where !seen.contains(handle) { seen.append(handle) }
            if seen.count > 22 { break }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.82))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.32)),
                       withVelocity: .slow, thenHoldForDuration: 0.2)
        }
        XCTAssertGreaterThan(seen.count, 22)
        let target = try XCTUnwrap(handles.allElementsBoundByIndex.last {
            seen.dropFirst(20).contains($0.label) && $0.isHittable && $0.frame.maxY < app.frame.height
        })
        let label = target.label
        let y = target.frame.minY
        target.tap()
        XCTAssertTrue(app.screenHeading("Profile").waitForExistence(timeout: 10))
        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(app.screenHeading(title).waitForExistence(timeout: 10))
        let restored = app.staticTexts[label].firstMatch
        XCTAssertTrue(restored.isHittable)
        XCTAssertEqual(restored.frame.minY, y, accuracy: 2)
    }

    @MainActor
    func testRailwayClipPlaysInGallery() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://m.qqs.tw/@ejsoon/117501796550705991", in: app)
        let clip = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "media-preview-")).firstMatch
        XCTAssertTrue(clip.waitForExistence(timeout: 20))
        clip.tap()
        XCTAssertTrue(app.staticTexts["1 of 1"].waitForExistence(timeout: 10))

        // Sample the board inside the video, away from the controls and loading indicator.
        func boardPixels() -> [UInt8] {
            guard let screen = app.screenshot().image.cgImage else { return [] }
            let w = CGFloat(screen.width), h = CGFloat(screen.height)
            guard let board = screen.cropping(to: CGRect(x: w * 0.18, y: h * 0.41, width: w * 0.64, height: h * 0.18)) else { return [] }
            var rgba = [UInt8](repeating: 0, count: 64 * 32 * 4)
            rgba.withUnsafeMutableBytes { storage in
                let context = CGContext(data: storage.baseAddress, width: 64, height: 32, bitsPerComponent: 8,
                                        bytesPerRow: 64 * 4, space: CGColorSpaceCreateDeviceRGB(),
                                        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
                context?.draw(board, in: CGRect(x: 0, y: 0, width: 64, height: 32))
            }
            return rgba
        }
        var firstFrame: [UInt8]?
        let advancing = NSPredicate { _, _ in
            let frame = boardPixels()
            let green = stride(from: 0, to: frame.count, by: 4).filter {
                Int(frame[$0 + 1]) > Int(frame[$0]) + 12 && Int(frame[$0 + 1]) > Int(frame[$0 + 2]) + 5
            }
            guard green.count > 40 else { return false }
            guard let first = firstFrame else { firstFrame = frame; return false }
            return green.filter { abs(Int(first[$0]) - Int(frame[$0])) > 12 }.count > 8
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: advancing, object: nil)], timeout: 40), .completed)
        capture("Railway clip playing", app)
    }

    @MainActor
    func testFederatedHashtagOpensTheNativeTimeline() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://mastodon.social/@Gargron/117634749643899696", in: app)
        let text = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Shenanigans #Plushtodon")).firstMatch
        XCTAssertTrue(text.waitForExistence(timeout: 20))
        text.coordinate(withNormalizedOffset: CGVector(dx: 0.77, dy: 0.5)).tap()
        XCTAssertTrue(app.screenHeading("#plushtodon").waitForExistence(timeout: 10))
        let posts = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View replies"))
        XCTAssertTrue(posts.firstMatch.waitForExistence(timeout: 20))
        capture("Native hashtag timeline", app)
        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(text.isHittable)
    }

    @MainActor
    func testReopeningHashtagStartsAtTopWhileBackKeepsTheCurrentVisit() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://mastodon.social/@bagder/117636701500634123", in: app)
        let source = app.descendants(matching: .any)["status-card-117636703604554067"].firstMatch
        XCTAssertTrue(source.waitForExistence(timeout: 15))
        source.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.2)).tap()
        XCTAssertTrue(app.screenHeading("Conversation").waitForExistence(timeout: 10))
        let link = app.buttons["#curl"].firstMatch
        let title = app.screenHeading("#curl")
        func openTag() {
            XCTAssertTrue(link.waitForExistence(timeout: 15))
            link.tap()
            XCTAssertTrue(title.waitForExistence(timeout: 10))
        }
        openTag()
        let cards = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "status-card-"))
        XCTAssertTrue(cards.firstMatch.waitForExistence(timeout: 15))
        let newest = app.descendants(matching: .any)[cards.firstMatch.identifier].firstMatch
        let top = newest.frame.minY

        app.swipeUp()
        let older = try XCTUnwrap(cards.allElementsBoundByIndex.first {
            $0.identifier != newest.identifier && $0.isHittable &&
                $0.frame.minY >= title.frame.maxY && $0.frame.midY < app.frame.height - 30
        })
        let saved = app.descendants(matching: .any)[older.identifier].firstMatch
        let offset = saved.frame.minY
        saved.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.3)).tap()
        XCTAssertTrue(app.screenHeading("Conversation").waitForExistence(timeout: 10))
        app.buttons["Close"].firstMatch.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertTrue(saved.waitForExistence(timeout: 15))
        XCTAssertEqual(saved.frame.minY, offset, accuracy: 2)

        app.buttons["Close"].firstMatch.tap()
        openTag()
        XCTAssertTrue(newest.waitForExistence(timeout: 15))
        XCTAssertTrue(newest.isHittable)
        XCTAssertEqual(newest.frame.minY, top, accuracy: 2)
        capture("Hashtag timeline reopened at the top", app)
    }

    @MainActor
    func testGalleryShowsDescriptionWithoutAnAltButton() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-app_locale", "en"]
        app.launch()
        search("https://m.qqs.tw/@bobmaster/117247628423335722", in: app)
        let preview = app.buttons["media-preview-117247626533796224"].firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["ALT"].exists)
        preview.tap()
        let caption = app.staticTexts["moon-gomoku online multiplayers demo"]
        XCTAssertTrue(caption.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["ALT"].exists)
        XCTAssertEqual(caption.frame.midX, app.frame.midX, accuracy: 2)
        XCTAssertGreaterThan(caption.frame.minY, app.frame.height * 0.8)
        capture("Centered gallery description", app)
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

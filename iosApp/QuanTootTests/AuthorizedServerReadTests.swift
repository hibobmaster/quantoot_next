import XCTest
import QuanTootShared

@MainActor
final class AuthorizedServerReadTests: XCTestCase {
    func testLocateOnlyThePendingAcceptancePost() async throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["QUANTOOT_LIVE_TESTS"] == "1",
                          "Authenticated server reads require explicit opt-in.")
        let marker = try XCTUnwrap(ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_MARKER"])
        XCTAssertNotNil(UUID(uuidString: marker))
        let store = AccountStore(
            platform: IosPlatformServices(),
            httpClientFactory: { HttpClient_iosKt.createHttpClient() },
            nowMillis: { KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)) }
        )
        try await store.restore()
        let state = store.state.value as? SessionState
        let api = try XCTUnwrap(store.client())
        defer { api.close() }
        let account = try await api.verifyCredentials()
        let page = try await api.timeline(
            source: TimelineAccountPosts(accountId: account.id, onlyMedia: false, pinned: false,
                                         excludeReplies: false, excludeReblogs: false),
            maxId: nil, sinceId: nil, limit: 40
        )
        let statuses = page.items.compactMap { $0 as? Status }
        let matches = statuses.filter { $0.content.contains(marker) }
        let receipt: [String: Any] = [
            "marker": marker,
            "cachedAccountID": state?.activeSession?.account.id ?? "",
            "verifiedAccountID": account.id,
            "verifiedAccount": account.acct,
            "recentStatusCount": statuses.count,
            "matches": matches.map {
                ["id": $0.id, "url": $0.url ?? "", "visibility": $0.visibility,
                 "inReplyToID": $0.inReplyToId ?? "", "authorID": $0.account.id]
            },
        ]
        let data = try JSONSerialization.data(withJSONObject: receipt, options: [.prettyPrinted, .sortedKeys])
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
        attachment.name = "Pending acceptance post server receipt"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertEqual(state?.activeSession?.account.id, account.id)
        XCTAssertEqual(matches.count, 1)
        XCTAssertEqual(matches.first?.visibility, "direct")
    }

    func testDeletedAcceptancePostIsAbsentOnServer() async throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["QUANTOOT_LIVE_TESTS"] == "1",
                          "Authenticated server reads require explicit opt-in.")
        let marker = try XCTUnwrap(ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_MARKER"])
        let statusID = try XCTUnwrap(ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_STATUS_ID"])
        let url = try XCTUnwrap(ProcessInfo.processInfo.environment["QUANTOOT_EXISTING_URL"])
        XCTAssertNotNil(UUID(uuidString: marker))
        XCTAssertTrue(!statusID.isEmpty && statusID.allSatisfy(\.isNumber))
        let store = AccountStore(
            platform: IosPlatformServices(),
            httpClientFactory: { HttpClient_iosKt.createHttpClient() },
            nowMillis: { KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)) }
        )
        try await store.restore()
        let state = store.state.value as? SessionState
        let api = try XCTUnwrap(store.client())
        defer { api.close() }
        let account = try await api.verifyCredentials()
        XCTAssertEqual(state?.activeSession?.account.id, account.id)
        XCTAssertEqual(url, "https://m.qqs.tw/@\(account.acct)/\(statusID)")
        let result = try await api.search(query: url, type: "statuses", offset: 0)
        XCTAssertTrue(result.statuses.isEmpty)
        let page = try await api.timeline(
            source: TimelineAccountPosts(accountId: account.id, onlyMedia: false, pinned: false,
                                         excludeReplies: false, excludeReblogs: false),
            maxId: nil, sinceId: nil, limit: 40
        )
        let statuses = page.items.compactMap { $0 as? Status }
        XCTAssertFalse(statuses.contains { $0.id == statusID || $0.content.contains(marker) })
        let receipt: [String: Any] = [
            "marker": marker, "statusID": statusID, "url": url,
            "verifiedAccountID": account.id, "verifiedAccount": account.acct,
            "successfulAuthenticatedSearch": true,
            "searchStatusCount": result.statuses.count,
            "recentAccountStatusCount": statuses.count,
            "absentFromSuccessfulAccountRead": true,
        ]
        let data = try JSONSerialization.data(withJSONObject: receipt, options: [.prettyPrinted, .sortedKeys])
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
        attachment.name = "Deleted acceptance post server receipt"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

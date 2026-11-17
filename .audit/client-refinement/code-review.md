# Independent code review

The initial gpt-6-astra review of the integrated presentation, draft and native save changes found no additional P1 or P2 beyond the separately assigned published-visibility correction. That correction is 88d090d.

A fresh gpt-6-astra review of 88d090d, bd8d175, 3700356 and the native acceptance helpers found one P2 in deletion evidence. The UI could show No content yet after a request error. Root accepted the finding and added AuthorizedServerReadTests.testDeletedAcceptancePostIsAbsentOnServer. It awaits successful account verification, exact-URL search and account timeline reads. Each deleted test post has an independent passing result and a receipt. The old UI-only receipt is retained as historical evidence and superseded, not used as proof of server absence.

A fresh gpt-6.1-sol reviewer inspected the corrected hosted test, its actual xcresult, receipts, natural-word tab measurement and corrected large-text screenshots. It reported no findings. Failed requests cannot return a successful search result because MastodonApi.request rejects non-2xx responses before decoding. The reviewer confirmed Federated remains on one line and the quoted content is visibly inset with a border and distinct background.

The final read-only suite first passed 10 scenarios and failed one because the old firstMatch selected a clipped reply control at y111 while a visible reply control was at y555. Root inspected the recording and hierarchy, changed only the test to choose a hittable reply control, and reran all 11 scenarios. All passed. Reading-position restoration remains unchanged.

The comment-review rerun, on gpt-6.1-sol, withdrew five previous findings on XcodeGen object labels. project.yml is their source and xcodegen regenerated project.pbxproj. The reviewer found no actionable hand-authored comment or suppression, no HOLD, no encoding recommendation and no open item. Zero comments were deleted or restored. ExperimentalCoroutinesApi opt-ins acknowledge the actual runCurrent dependency annotation and do not suppress correctness checks. The photo verifier's first line is a shebang.

Root reviewed the production diffs and retained existing status, feed and account ownership. The bounded poll-edit browser handoff remains disclosed in CLIENT_PARITY.md. These code reviews do not establish physical Release delivery, which requires separate build, signature, install and device receipts.

# Interaction correction scope

User reports that Profile reserves fixed header height and leaves Posts cramped, QR and sharing actions are poorly arranged, full-screen media lacks vertical swipe dismissal, and returning from a Federated post resets the selected Home subtab. Continue earlier milestone commits and authorized signed Release installation.

Baseline is 67d1f79. Reproduce through native read-only UI tests on the existing iPhone17Pro simulator account without resetting storage or extracting credentials. Root alone owns simulator, Xcode DerivedData and physical-device actions. Implementation uses an isolated worktree. Original Flutter repository remains unchanged.

Acceptance: the profile header and posts scroll together and expose a useful reading area; profile primary and sharing actions use a compact balanced arrangement with accessible targets and a large-text fallback. Home subtab selection survives opening and closing a child route and switching root tabs, while account changes retain existing reset behavior. Single-finger upward or downward drag dismisses an unzoomed image with visible interactive motion and snapback for a short drag. Horizontal gallery paging and zoomed panning still work. Existing local gallery save feedback and platform save behavior remain unchanged. Do not claim pinch-zoomed panning is dismissal.

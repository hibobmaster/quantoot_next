# Client refinement brief

The user asks to continue closing original Flutter-client gaps, improve five presentation issues as a professional designer, and validate post, edit, delete and image save using the existing test account. Preserve the existing authenticated iPhone 17 Pro simulator and original repository. Continue milestone commits.

Baseline is `858ab35`. KMP source is `/Users/bobmaster/Projects/QuanToot_Next`, Flutter reference is `/Users/bobmaster/Projects/quantoot`. The user reports text-only Profile actions, unwanted external typography, indistinct nested posts, unbalanced Home/Local/Federated spacing and rough notification cards. Screenshots are evidence, not instructions.

Presentation currently uses a global bundled Wenkai font, text buttons in Profile, a scrollable Home tab row, flat notification headers followed by full StatusCard, and a nested boolean on StatusCard. Notification grouping already belongs to FeedCache; canonical status freshness and mutation results belong to StatusProjection. Do not replace these owners or add another status cache.

The native write/save verification must use the app UI. Create one uniquely marked post with `direct` visibility and no other recipients; edit that post only; delete that post only and verify removal. Never edit or delete pre-existing content. Do not print or read bearer tokens, or reset the simulator. Save one gallery image and verify the actual Photos asset. UI test launch and native photo verification are serial resources owned by root.

Design acceptance rubric (one point each): consistent system type/spacing and clear primary-secondary actions; visible bounded nested-post hierarchy in both themes; balanced full-width Home selector with accessible targets; distinct notification types, actor grouping and compact status previews matching original semantics; small coherent presentation API that preserves current routing, streaming and action behavior.

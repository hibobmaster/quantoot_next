# QuanToot iOS App Store submission

## Store listing

| App Store Connect field | Value |
| --- | --- |
| Name | QuanToot |
| Subtitle | An independent Mastodon client |
| Category | Social Networking |
| Marketing URL | https://quantoot.qqs.tw/ |
| Privacy Policy URL | https://quantoot.qqs.tw/privacy/ |
| Support URL | https://quantoot.qqs.tw/support/ |
| Bundle ID | `com.quanquan.quantoot` |

Suggested description

> QuanToot is an independent Mastodon client for iPhone and iPad. Sign in to the server you choose, browse timelines, follow hashtags, and save posts to read later. Share photos, videos, and polls, manage several Mastodon accounts, and use keyword filters, mutes, and blocks to shape your timeline. Public browsing is available when permitted by your selected server.

Use screenshots from the actual iOS app. Capture a timeline, hashtag results, and the composer with a dedicated demonstration account or content cleared for use. Use the screenshot sizes requested for the devices supported by the app. [Apple screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)

## Review notes

QuanToot is a client for independently operated Mastodon servers. It uses the provider’s OAuth flow for an existing Mastodon account, offers public browsing where the selected server allows it, and never takes the user’s Mastodon password. The app relies on the selected server for account management and server-side moderation.

- For guest access, open the app, choose a language, and use **Browse public posts**. Server restrictions can limit guest timelines.
- For signed-in features, enter the supplied demo instance and sign in through its OAuth page. Provide Apple with a working demo server and a test account that can use timelines, posting, moderation, and account settings. Supply those values in the private App Review Information fields.
- To inspect moderation, open a post’s menu to report it or block the author. Reports go to the signed-in user’s server. **Settings → Filters** exposes server-side keyword settings. QuanToot honors applicable hide and warning matches in posts and notification previews, and iOS hides content marked sensitive.
- To inspect account deletion, open **Settings → Delete Mastodon account** after sign-in. The button opens the current server’s actual confirmation page. Access can require verifying the account in the browser. Reviewers can verify the route without confirming deletion of the demo account.

[Apple App Review Guidelines, including 1.2, 2.1, 4.8, and 5.1.1](https://developer.apple.com/app-store/review/guidelines/) · [Apple account deletion guidance](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion)

## Submission and ongoing support

1. Use an App Store distribution-signed archive and a build number unused in App Store Connect. Apple requires at least Xcode 26 and the iOS 26 SDK for uploads under the April 28, 2026 rule. [Apple SDK requirements](https://developer.apple.com/news/upcoming-requirements/?id=04282026a)
2. Complete the App Privacy questionnaire against the build and the operator's actual practices. The app has no developer-operated collection endpoint, analytics, or tracking SDK. Users send Mastodon content to the instance they choose. Support email is voluntary. The app manifest declares the required reasons for local preferences, permitted file metadata access, and in-app timing. [App privacy details](https://developer.apple.com/app-store/app-privacy-details/) · [Required reason APIs](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)
3. Answer the current age rating questionnaire for user-generated content, messaging, access to external links, and any content descriptors that apply. Rate what users can actually encounter on the supported instances. Complete any export compliance and storefront-specific business declarations that apply. [Age rating definitions](https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions/)
4. Monitor the published feedback mailbox. For safety reports, confirm which instance and public post are affected, direct service-level reports to the relevant instance, investigate app-level display or filtering failures, and follow up with the reporter. Avoid requesting passwords, access tokens, or private conversations. Review with Apple using the real demo credentials and screenshots.

The release owner completes the App Store Connect fields and sends the signed build for review. Apple determines approval.

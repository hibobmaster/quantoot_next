# QuanToot website

The homepage, privacy policy, and support pages are available in English,
Simplified Chinese, and Traditional Chinese. The homepage uses fictional posts
for its timeline demo.

Run `npm run check` here to check pages and asset paths. Run `npm ci` and then
`npx wrangler pages dev dist --port 4173` to preview locally with the download API.

| Route | Purpose |
| --- | --- |
| [quantoot.qqs.tw](https://quantoot.qqs.tw/) | Product overview and downloads |
| [/privacy/](https://quantoot.qqs.tw/privacy/) | App Store privacy policy URL |
| [/support/](https://quantoot.qqs.tw/support/) | App Store support URL and account deletion help |

## Android downloads

The homepage queries the `/api/android` Pages Function. It reads the latest
stable release from the public Forgejo API and returns the official APK and
checksum links. Successful responses can be cached for five minutes. APKs are
served by Forgejo, and new stable releases appear without a website deployment.

## Cloudflare Pages

The Pages project is named `quantoot`. It uses Direct Upload with `main` as the
production branch and `quantoot.qqs.tw` as its custom domain. Forgejo runs the
static checks on pull requests. Pushes to `main` that touch this directory or
the website workflow deploy the checked files. The workflow can also run on
demand against `main`.

Set the repository secrets `CLOUDFLARE_ACCOUNT_ID` and
`CLOUDFLARE_PAGES_TOKEN`. The deployment token needs Cloudflare Pages edit
permission for the account that owns the project. Configure the custom domain
under the Pages project. Domain setup and DNS administration use their own
authorization.

[Cloudflare Direct Upload](https://developers.cloudflare.com/pages/get-started/direct-upload/)
· [Custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/)

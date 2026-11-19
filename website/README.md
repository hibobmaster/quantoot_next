# QuanToot website

Serve the static files in `dist/`. The homepage offers English and Simplified
Chinese and uses fictional posts for its timeline demo.

Run `npm run check` here to check pages and asset paths. Preview locally with
`python3 -m http.server 4173 --bind 127.0.0.1 --directory dist`.

| Route | Purpose |
| --- | --- |
| [quantoot.qqs.tw](https://quantoot.qqs.tw/) | Product overview and downloads |
| [/privacy/](https://quantoot.qqs.tw/privacy/) | App Store privacy policy URL |
| [/support/](https://quantoot.qqs.tw/support/) | App Store support URL and account deletion help |

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

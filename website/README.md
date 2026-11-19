# QuanToot website

Official marketing, privacy policy, and support website for **QuanToot** — an independent Kotlin Multiplatform Mastodon client for iOS and Android.

All pages are available in **English**, **Simplified Chinese**, and **Traditional Chinese**, with strict Content Security Policy compliance (`default-src 'self'`), zero external CDN calls, and zero analytics or tracking scripts.

## Structure

- `src/styles/` — Modular CSS design system (tokens aligned to `Appearance.kt` Day/Night schemes, layout, hero, interactive simulator, adaptive showcase, document pages)
- `src/scripts/` — Modular TypeScript (`i18n.ts`, `theme.ts`, `motion.ts`, `interactive-app.ts`, `downloads.ts`, `main.ts`)
- `src/pages/` — Static HTML templates for `/`, `/privacy/`, and `/support/`
- `functions/api/android.js` — Cloudflare Pages Function fetching the latest signed Android release and SHA-256 checksums from Forgejo
- `dist/` — Pre-built static output deployed directly to Cloudflare Pages

## Commands

```sh
# Install dependencies (pinned Wrangler + esbuild)
npm ci

# Rebuild dist/ from src/
npm run build

# Verify pages, canonical URLs, local links, and CSP rules
npm run check

# Preview locally with the /api/android Cloudflare Pages Function
npm run dev
```

| Route | Purpose |
| --- | --- |
| [quantoot.qqs.tw](https://quantoot.qqs.tw/) | Product overview, interactive walkthrough, adaptive showcase, and downloads |
| [/privacy/](https://quantoot.qqs.tw/privacy/) | App Store privacy policy URL |
| [/support/](https://quantoot.qqs.tw/support/) | App Store support URL, safety reporting, and account deletion help |

## Android downloads

The homepage queries the `/api/android` Pages Function. It reads the latest stable release from the public Forgejo API and returns the official per-ABI signed APK links (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) and `SHA256SUMS.txt`. Successful responses are cached for five minutes.

## Cloudflare Pages

The Pages project is named `quantoot`. It uses Direct Upload from `website/dist` with `main` as the production branch and `quantoot.qqs.tw` as its custom domain. Forgejo runs `npm run check --prefix website` on pull requests and deploys on pushes to `main` via `.forgejo/workflows/website.yml`.

Set the repository secrets `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_PAGES_TOKEN`.

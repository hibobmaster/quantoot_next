#!/bin/bash
set -euo pipefail

quantoot_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$quantoot_root"

case "${1:-}" in
  ""|--ios) ;;
  *) printf 'Usage: %s [--ios]\n' "$0" >&2; exit 2 ;;
esac

./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug --console=plain

if [[ "${1:-}" == --ios ]]; then
  xcodebuild -project iosApp/QuanToot.xcodeproj -scheme QuanToot \
    -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
fi

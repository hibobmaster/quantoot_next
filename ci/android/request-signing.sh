#!/usr/bin/env bash
set -euo pipefail
umask 077

work=$(mktemp -d)
trap 'rm -r -- "$work"' EXIT
outputs=androidApp/build/outputs/apk/release
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    cp "$outputs/androidApp-${abi}-release-unsigned.apk" "$work/$abi.apk"
done
(cd "$work" && zip -q -0 unsigned.zip armeabi-v7a.apk arm64-v8a.apk x86.apk x86_64.apk)

# The Forgejo-signed token grants permission for this tag without exposing the key.
token=$(curl --fail --silent --show-error --max-time 30 \
    --header "Authorization: Bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
    "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=quantoot-android-release" | jq -er '.value')
printf 'Authorization: Bearer %s\n' "$token" > "$work/authorization"
unset token
curl --fail-with-body --silent --show-error --max-time 300 \
    --header "@$work/authorization" --header 'Content-Type: application/zip' \
    --data-binary "@$work/unsigned.zip" --output "$work/signed.zip" \
    http://quantoot-android-signer:8080/sign

destination=androidApp/build/ci-release
mkdir -p "$destination"
unzip -oq "$work/signed.zip" -d "$destination"
(cd "$destination" && sha256sum --check SHA256SUMS.txt)

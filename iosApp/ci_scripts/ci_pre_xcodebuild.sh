#!/bin/bash
set -euo pipefail

case "${CI_XCODEBUILD_ACTION:-}" in
    build|build-for-testing|archive|analyze)
        exec python3 "$(dirname "$0")/fetch-androidx-ios.py"
        ;;
    *)
        exit 0
        ;;
esac

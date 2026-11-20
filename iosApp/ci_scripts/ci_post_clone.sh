#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
source ./java-home.sh

if ! quantoot_java_home >/dev/null; then
    HOMEBREW_NO_AUTO_UPDATE=1 brew install openjdk@17
fi

java_home=$(quantoot_java_home)
"$java_home/bin/java" -version

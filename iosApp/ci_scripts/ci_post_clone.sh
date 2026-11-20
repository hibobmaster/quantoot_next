#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
source ./java-home.sh

if ! quantoot_java_home >/dev/null; then
    HOMEBREW_NO_AUTO_UPDATE=1 brew install openjdk@17
fi

java_home=$(quantoot_java_home)
"$java_home/bin/java" -version

if [ "${CI_XCODE_CLOUD:-}" = "TRUE" ]; then
    # Gradle uses JVM proxy properties instead of the worker's proxy variables.
    # Keep the settings in Gradle's user home so they reach the Xcode build phase.
    python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlsplit

properties = {}
for protocol in ("http", "https"):
    variable = f"{protocol.upper()}_PROXY"
    value = os.environ.get(variable) or os.environ.get(f"{protocol}_proxy")
    if not value:
        continue
    proxy = urlsplit(value if "://" in value else f"http://{value}")
    if proxy.scheme != "http" or not proxy.hostname or proxy.username is not None:
        raise SystemExit(f"Unsupported {variable} format in the Xcode Cloud worker.")
    properties[f"{protocol}.proxyHost"] = proxy.hostname
    properties[f"{protocol}.proxyPort"] = proxy.port or 80

if properties:
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
    gradle_home.mkdir(parents=True, exist_ok=True)
    with (gradle_home / "gradle.properties").open("a") as config:
        for key, value in properties.items():
            config.write(f"\nsystemProp.{key}={value}\n")
    print("Configured Gradle to use the Xcode Cloud proxy.")
else:
    print("The Xcode Cloud worker did not provide proxy variables.")
PY
fi

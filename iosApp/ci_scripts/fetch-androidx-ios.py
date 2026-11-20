"""Populate the Xcode Cloud Maven repository with verified AndroidX artifacts."""

from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from http.client import HTTPException
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.request import urlopen


SOURCES = (
    "https://maven.aliyun.com/repository/google/",
    "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/",
    "https://dl.google.com/dl/android/maven2/",
)
SCRIPT_DIR = Path(__file__).resolve().parent
LOCAL_REPOSITORY = SCRIPT_DIR.parents[1] / ".gradle/xcode-cloud-google-maven"


def fetch_artifact(entry):
    checksum, relative_path = entry.split(maxsplit=1)
    artifact = LOCAL_REPOSITORY / relative_path
    if artifact.is_file() and sha256(artifact.read_bytes()).hexdigest() == checksum:
        return

    failures = []
    for source in SOURCES:
        try:
            with urlopen(source + relative_path, timeout=12) as response:
                content = response.read()
        except (OSError, HTTPException) as error:
            failures.append(f"{source}: {error}")
            continue
        if sha256(content).hexdigest() != checksum:
            failures.append(f"{source}: SHA-256 mismatch")
            continue

        artifact.parent.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(dir=artifact.parent) as temporary:
            download = Path(temporary) / artifact.name
            download.write_bytes(content)
            download.replace(artifact)
        return

    raise RuntimeError(f"Could not verify {relative_path}\n" + "\n".join(failures))


def main():
    manifest = SCRIPT_DIR / "androidx-ios.sha256"
    artifacts = [
        line for line in manifest.read_text().splitlines()
        if line and not line.startswith("#")
    ]
    with ThreadPoolExecutor(max_workers=8) as workers:
        list(workers.map(fetch_artifact, artifacts))
    print(f"Verified {len(artifacts)} AndroidX files for the Xcode Cloud Maven repository.")


if __name__ == "__main__":
    main()

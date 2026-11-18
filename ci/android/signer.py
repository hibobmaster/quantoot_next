"""Sign QuanToot releases authorized by a Forgejo Actions OIDC token."""

from hashlib import sha256
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import os
import re
import shutil
import socket
import subprocess
import tempfile
import zipfile

import jwt


REPOSITORY = "bobmaster/quantoot_next"
ISSUER = "https://git.qqs.tw/api/actions"
AUDIENCE = "quantoot-android-release"
WORKFLOW = ".forgejo/workflows/android.yml"
TAG = re.compile(r"refs/tags/v(\d+\.\d+\.\d+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?)", re.ASCII)
ABIS = ("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
LIMIT = 32 * 1024 * 1024
JWKS = jwt.PyJWKClient(f"{ISSUER}/.well-known/keys")


def authorized_version(token):
    key = JWKS.get_signing_key_from_jwt(token)
    claims = jwt.decode(
        token, key.key, algorithms=["RS256"], audience=AUDIENCE, issuer=ISSUER,
        options={"require": ["exp", "iat", "nbf", "sub"]},
    )
    ref = claims.get("ref", "")
    tag = TAG.fullmatch(ref)
    actor = os.environ["QUANTOOT_RELEASE_ACTOR"]
    owner, repo = REPOSITORY.split("/")
    owner_id = claims.get("repository_owner_id", "")
    repo_id = claims.get("repository_id", "")
    subjects = {f"repo:{REPOSITORY}:ref:{ref}"}
    if re.fullmatch(r"[0-9]+", str(owner_id)) and re.fullmatch(r"[0-9]+", str(repo_id)):
        subjects.add(f"repo:{owner}-{owner_id}/{repo}-{repo_id}:ref:{ref}")

    if (
        not tag
        or len(ref) > 100
        or claims.get("event_name") != "push"
        or claims.get("ref_type") != "tag"
        or claims.get("actor") != actor
        or claims.get("repository") != REPOSITORY
        or claims.get("workflow_ref") != f"{REPOSITORY}/{WORKFLOW}@{ref}"
        or claims["sub"] not in subjects
    ):
        raise jwt.InvalidTokenError("Expected a release tag push by the release owner")
    return tag[1]


def tool(name, *args, env=None):
    binary = Path(os.environ["ANDROID_HOME"]) / "build-tools/37.0.0" / name
    return subprocess.run(
        [binary, *args], cwd="/tmp", env=env, check=True, text=True, capture_output=True, timeout=45,
    ).stdout


def release_version_code(apk, abi, version):
    badging = tool("aapt2", "dump", "badging", apk)
    package = re.search(r"^package: name='com\.quanquan\.quantoot' versionCode='([0-9]+)' versionName='([^']*)'", badging, re.MULTILINE)
    native_code = re.search(r"^native-code: '([^']+)'\s*$", badging, re.MULTILINE)
    if not package or package[2] != version or not native_code or native_code[1] != abi:
        raise ValueError(f"Version, application ID or ABI mismatch for {abi}")
    if re.search(r"^application-debuggable", badging, re.MULTILINE):
        raise ValueError(f"Debuggable APK for {abi}")
    return int(package[1])


def sign_archive(work, version):
    credentials = Path("/run/quantoot-signing")
    fingerprint = os.environ["QUANTOOT_CERT_SHA256"]
    signing_env = {
        **os.environ,
        "QUANTOOT_STORE_PASS": (credentials / "store-password").read_text(),
        "QUANTOOT_KEY_PASS": (credentials / "key-password").read_text(),
    }
    with zipfile.ZipFile(work / "unsigned.zip") as archive:
        if sorted(archive.namelist()) != sorted(f"{abi}.apk" for abi in ABIS):
            raise ValueError("Expected exactly four ABI APKs")
        versions = []
        for abi in ABIS:
            name = f"{abi}.apk"
            info = archive.getinfo(name)
            if not 0 < info.file_size <= LIMIT:
                raise ValueError(f"Invalid APK size for {abi}")
            with archive.open(info) as source, (work / name).open("wb") as destination:
                shutil.copyfileobj(source, destination)
            versions.append(release_version_code(work / name, abi, version))
        version_base = versions[0] - 1
        if version_base % 10 or versions != list(range(version_base + 1, version_base + 5)):
            raise ValueError("Invalid ABI version codes")

    checksums = []
    output = work / "signed.zip"
    with zipfile.ZipFile(output, "w") as archive:
        for abi in ABIS:
            aligned = work / "aligned.apk"
            signed = work / f"QuanToot-{version}-{abi}.apk"
            tool("zipalign", "-P", "16", "-f", "4", work / f"{abi}.apk", aligned)
            tool("apksigner", "sign", "--ks", credentials / "keystore.jks", "--ks-key-alias", "upload",
                 "--ks-pass", "env:QUANTOOT_STORE_PASS", "--key-pass", "env:QUANTOOT_KEY_PASS",
                 "--v4-signing-enabled", "false", "--out", signed, aligned, env=signing_env)
            tool("zipalign", "-P", "16", "-c", "4", signed)
            verification = tool("apksigner", "verify", "--verbose", "--print-certs", signed)
            digests = set(re.findall(r"certificate SHA-256 digest: ([a-f0-9]+)", verification))
            if digests != {fingerprint} or "Verified using v2 scheme (APK Signature Scheme v2): true" not in verification:
                raise ValueError(f"Unexpected APK signature for {abi}")
            with signed.open("rb") as handle:
                checksums.append(f"{sha256(handle.read()).hexdigest()}  {signed.name}\n")
            archive.write(signed, signed.name)
        archive.writestr("SHA256SUMS.txt", "".join(checksums))
    return output


class Signer(BaseHTTPRequestHandler):
    timeout = 30

    def do_POST(self):
        if self.path != "/sign":
            self.send_error(404)
            return
        scheme, _, token = self.headers.get("Authorization", "").partition(" ")
        try:
            if scheme.lower() != "bearer" or not token:
                raise jwt.InvalidTokenError("Bearer token required")
            version = authorized_version(token)
        except jwt.PyJWTError:
            self.send_error(403, "Release authorization failed")
            return

        length = self.headers.get("Content-Length", "")
        if not re.fullmatch(r"[0-9]{1,9}", length) or not 0 < int(length) <= 4 * LIMIT:
            self.send_error(413, "Unexpected APK archive size")
            return
        with tempfile.TemporaryDirectory() as directory:
            try:
                work = Path(directory)
                remaining = int(length)
                with (work / "unsigned.zip").open("wb") as archive:
                    while remaining:
                        chunk = self.rfile.read(min(remaining, 1024 * 1024))
                        if not chunk:
                            raise ValueError("Incomplete APK archive")
                        archive.write(chunk)
                        remaining -= len(chunk)
                output = sign_archive(work, version)
            except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
                self.log_error("Could not sign v%s (%s)", version, type(error).__name__)
                self.send_error(422, "APK validation or signing failed")
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Length", str(output.stat().st_size))
            self.end_headers()
            with output.open("rb") as archive:
                shutil.copyfileobj(archive, self.wfile)


if __name__ == "__main__":
    env = os.environ
    if not env["QUANTOOT_RELEASE_ACTOR"] or not re.fullmatch(r"[a-f0-9]{64}", env["QUANTOOT_CERT_SHA256"]):
        raise SystemExit("Configure release actor and the expected signing certificate")
    socket.setdefaulttimeout(10)
    HTTPServer(("", 8080), Signer).serve_forever()

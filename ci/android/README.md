# QuanToot Android CI

Register a repository-level Linux x86_64 runner in Forgejo with the `android-ci` label. Pushes to `main` and pull requests targeting `main` run the shared tests and build a debug APK. Android CI skips changes limited to the website, `docs/`, `.audit/`, iOS-specific code, or GitHub workflows. Forgejo Actions retains test report artifacts for 14 days. Pushing a version tag such as `v0.6.5` on a commit in the history of `main` runs the tests and produces four signed, ABI-specific release APKs.

## Runner and signing service

Build both images on the VPS. Run these commands from the repository root using `ci/android` as the build context.

```sh
docker build --platform linux/amd64 --target runner -t quantoot-android-ci:37 ci/android
docker build --platform linux/amd64 --target signer -t quantoot-android-signer:37 ci/android
docker network create quantoot-ci
```

Enable Actions in the Forgejo repository settings and create a repository-level runner. Merge the `server.connections` configuration provided by Forgejo into `runner.example.yml`, then install it as `/opt/quantoot-ci/runner.yml` with ownership `root:quantoot-runner` and mode `0640`. The supplied systemd unit starts `/opt/quantoot-ci/bin/forgejo-runner` under the `quantoot-runner` system account. The runner uses a dedicated Docker network and blocks arbitrary host volume mounts and Docker socket access from job containers.

The workflow caches the Gradle Wrapper, downloaded dependencies, and dependency resolution metadata. The cache key changes with the Wrapper, version catalog, and repository configuration. Successful test jobs triggered by pushes to `main` write to the shared cache. Pull request and release tag jobs can read it. Runner v13.2.1 removes entries that have been unused for seven days or were created more than 30 days ago. The cache resides at `/var/lib/quantoot-ci/cache` on the VPS.

Before enabling the cache, set `cache.host` to the gateway of the `quantoot-ci` Docker network. Install `nftables`, then install `cache-firewall.sh` at `/opt/quantoot-ci/cache-firewall.sh` with ownership `root:root` and mode `0755`. The systemd unit restricts port access before starting the runner. Build containers reach the cache proxy on port `49231`. The host accesses the internal cache on port `49232` over loopback. Find the gateway with the following command.

```sh
docker network inspect -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}' quantoot-ci
```

The signing container runs with UID and GID `10001:10001`. Place `keystore.jks`, `store-password`, and `key-password` in a private directory on the host and make them readable by that UID. Each password file contains only the password itself. The current key alias is `upload`. The command below uses the certificate fingerprint and the signing directory at `/opt/quantoot-ci/private/signing` on the VPS.

```sh
docker run -d --name quantoot-android-signer --restart unless-stopped \
  --network quantoot-ci --user 10001:10001 --read-only --cap-drop ALL \
  --security-opt no-new-privileges --memory 768m --pids-limit 128 \
  --tmpfs /tmp:rw,noexec,nosuid,nodev,size=512m,mode=1777 \
  --mount type=bind,src=/opt/quantoot-ci/private/signing,dst=/run/quantoot-signing,readonly \
  -e QUANTOOT_RELEASE_ACTOR=bobmaster \
  -e QUANTOOT_CERT_SHA256=19dbe161f618824b285929b00eda4e6d05d377b962d82a4555dbef0a8f1b413b \
  quantoot-android-signer:37
```

The signing service validates the Forgejo OIDC token's issuer, audience, repository, workflow path, release tag, and release actor. The signing files are available only to the signing service. Forgejo 17 sets the `ref_protected` OIDC claim to `false`, so the signer authorizes a configured release actor. Use the repository's tag protection rules to restrict creation of `v*` tags to that account.

## Releases

Update `versionName` and increment `versionCode` in `androidApp/build.gradle.kts`, then merge the changes. The `bobmaster` release account pushes a version tag that matches `versionName`. The signing service checks each APK's package name, ABI, version, certificate, and 16 KB alignment.

After verification, the workflow uses Forgejo's temporary job token to upload the four APKs and `SHA256SUMS.txt` to the release for that tag. The workflow publishes a new release once all five assets are uploaded. Tags with version suffixes are published as prereleases. Rerunning a tag with the same checksum manifest restores missing assets and preserves the release notes. The job fails and preserves existing assets if it finds an unrecognized asset with the same name or a different checksum manifest. Release assets remain available for download, and the Actions retention period applies to test reports.

The workflow generates release notes from Git commits. The first release uses the repository history. Later stable releases start at the previous stable tag, while prereleases also consider earlier prerelease tags. Existing manually written release notes are preserved.

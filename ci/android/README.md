# QuanToot Android CI

Forgejo 只需给本仓库注册带 `android-ci` 标签的 Linux x86_64 Runner。PR 和 `main` 运行共享测试并构建 Debug APK。推送指向 `main` 历史的版本标签（例如 `v0.6.5`）时，工作流运行测试，生成并签名四个 ABI 的 Release APK。APK 和校验清单可在该次 Actions 运行的构件中下载。

## Runner 和签名服务

在 VPS 上构建这两个镜像。唯一需要的构建上下文是本目录。

```sh
docker build --platform linux/amd64 --target runner -t quantoot-android-ci:37 ci/android
docker build --platform linux/amd64 --target signer -t quantoot-android-signer:37 ci/android
docker network create quantoot-ci
```

在 Forgejo 仓库设置中启用 Actions，并创建仓库级 Runner。将 Forgejo 提供的 `server.connections` 合并到 `runner.example.yml`，将配置以 `root:quantoot-runner`、`0640` 安装为 `/opt/quantoot-ci/runner.yml`。部署使用 `quantoot-runner` 系统账户，由随附的 systemd 单元启动 `/opt/quantoot-ci/bin/forgejo-runner`。Runner 使用独立 Docker 网络，禁用任意宿主机卷和任务容器里的 Docker socket。

签名容器以 UID/GID `10001:10001` 运行。将 `keystore.jks`、`store-password`、`key-password` 三个文件放到宿主机的私有目录并由该 UID 读。密码文件只保存密码本身。当前密钥别名为 `upload`，证书指纹见下方。以下命令中的签名目录在 VPS 上的 `/opt/quantoot-ci/private/signing`。

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

签名服务根据 Forgejo OIDC 令牌校验实例、受众、仓库、工作流路径、版本标签和发布账户。普通 CI 无法读取签名文件。Forgejo 17 的 `ref_protected` OIDC 声明仍固定为 false，所以签名服务以固定的发布账户进行授权。在仓库的标签保护规则里，把 `v*` 的创建权限限制给发布账户。

## 发布

先更新并合并 `androidApp/build.gradle.kts` 的 `versionName` 和递增的 Android 版本号，再由 `bobmaster` 推送与 `versionName` 一致的标签。签名服务核对每个 APK 的包名、ABI、版本、证书和 16 KB 对齐。签名和校验通过后，Actions 才会上传四个 APK 和 `SHA256SUMS.txt`。

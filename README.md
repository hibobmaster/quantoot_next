# QuanToot

QuanToot 是使用 Kotlin Multiplatform 和 Compose Multiplatform 重构的 Mastodon 客户端。Android 和 iOS 共用界面、网络、账户状态与业务逻辑。

用户自行选择实例。应用支持公开浏览、OAuth 登录、多账户、时间线、搜索、通知、发帖、媒体、投票、个人资料和账户设置。

## 开发环境

- JDK 17。
- Android SDK 37。
- macOS 和 Xcode，用于构建 iOS。最低 iOS 版本为 15.0。
- Android 最低版本为 API 26。

仓库固定 Kotlin 2.4.21、Compose Multiplatform 1.12.2、AGP 9.3.3 和 Gradle 9.5.0。版本定义位于 `gradle/libs.versions.toml`。

版本依据为 [Kotlin 发布记录](https://kotlinlang.org/docs/releases.html)、[Compose 兼容性说明](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)和 [AGP 9.3 发布说明](https://developer.android.com/build/releases/agp-9-3-0-release-notes)。

## 构建 Android

将 `ANDROID_HOME` 指向 Android SDK，或者在未提交的 `local.properties` 中配置 `sdk.dir`。

```sh
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

调试 APK 输出到 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。在 Android Studio 中打开仓库，选择 `androidApp` 运行。

发布包需要配置自己的签名。仓库不包含签名密钥，也不为 release 构建使用 debug 签名。

## 构建 iOS

打开 `iosApp/QuanToot.xcodeproj`，选择 `QuanToot` scheme 和 iOS 模拟器，然后运行。构建阶段会自动调用 Gradle 生成共享 framework。

```sh
xcodebuild -project iosApp/QuanToot.xcodeproj \
  -scheme QuanToot -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

真机运行需要在 Xcode 中设置自己的开发团队。修改 `iosApp/project.yml` 后，运行 `xcodegen generate --spec iosApp/project.yml` 更新工程。

## 验证

```sh
./scripts/verify.sh
./scripts/verify.sh --ios
```

共享测试检查 OAuth、账户隔离、分页、媒体处理、响应限制和时间线状态。原生测试还检查 Android Keystore、iOS Keychain 及旧 Flutter 凭据的迁移。

迁移覆盖和验证边界见 [迁移记录](docs/MIGRATION.md)。模块职责见 [架构说明](docs/ARCHITECTURE.md)。

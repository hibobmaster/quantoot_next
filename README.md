# QuanToot

QuanToot is a Mastodon client built with Kotlin Multiplatform and Compose Multiplatform. Its Android and iOS apps share the user interface, networking, account state, and application logic.

Choose your Mastodon instance and browse public timelines without signing in. Sign in with OAuth to use multiple accounts, follow timelines, search, view notifications, publish posts with media and polls, and manage profiles and account settings.

## Development requirements

- JDK 17
- Android SDK 37
- macOS and Xcode for iOS builds
- Android API 26 or later and iOS 15.0 or later on target devices

The project pins Kotlin 2.4.21, Compose Multiplatform 1.12.2, and Android Gradle Plugin 9.3.3 in `gradle/libs.versions.toml`. The Gradle Wrapper pins Gradle 9.9.1 in `gradle/wrapper/gradle-wrapper.properties`.

See the [Kotlin release notes](https://kotlinlang.org/docs/releases.html), [Compose compatibility guide](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), and [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes) for version compatibility.

## Build for Android

Set `ANDROID_HOME` to the Android SDK directory or set `sdk.dir` in your local `local.properties` file.

```sh
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

The debug APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. To run the app from Android Studio, open the repository and select the `androidApp` run configuration.

Configure your own signing key for release builds. Keep signing keys outside the repository and use a release signing configuration.

## Build for iOS

Open `iosApp/QuanToot.xcodeproj` in Xcode, select the `QuanToot` scheme and an iOS simulator, then run the app. Xcode invokes Gradle during the build to generate the shared framework.

```sh
xcodebuild -project iosApp/QuanToot.xcodeproj \
  -scheme QuanToot -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

To run on a physical device, select your Apple developer team in Xcode. After editing `iosApp/project.yml`, regenerate the project with `xcodegen generate --spec iosApp/project.yml`.

## Verification

```sh
./scripts/verify.sh
./scripts/verify.sh --ios
```

Shared tests cover OAuth, account isolation, pagination, media handling, response limits, and timeline state. Platform tests also cover Android Keystore, iOS Keychain, and migration of credentials from the earlier Flutter app.

See the [architecture guide](docs/ARCHITECTURE.md) for module responsibilities.

## License

QuanToot is licensed under the [MIT License](LICENSE).

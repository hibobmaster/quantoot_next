# Temporary Profile motion probe

This worktree tests whether the accepted Profile controller can retain the header and category geometry across body replacements, including empty bodies. The reusable core is commit `98890e0e3e1154e4f56e211fd491ee609b866b94` on `codex/navigation-profile-065`, based on `8a480de9e02ecf301055ab25321f0effe2036794`.

All native results are pending. The worker ran no Xcode build, XCTest, simulator action, device action, or live-account operation. The compile slot was released to root after the final Gradle command completed. Do not infer native motion, accessibility, refresh, or live-feed acceptance from the Kotlin compile.

## Reproduce the probe

The worktree already contains the temporary files. To reproduce it in a clean checkout of the core commit, apply `temporary-probe.patch` from this directory with `git apply`. The patch contains exactly these six files:

- `shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/TemporaryProfileMotionProbe.kt`
- `shared/src/iosMain/kotlin/com/quanquan/quantoot/TemporaryProfileMotionProbeController.kt`
- `iosApp/QuanToot/QuanTootApp.swift`
- `iosApp/QuanTootTests/TemporaryProfileAccessibilityProbeTests.swift`
- `iosApp/QuanTootUITests/TemporaryProfileMotionProbeTests.swift`
- `iosApp/QuanToot.xcodeproj/project.pbxproj`

The exported Kotlin function is `temporaryProfileMotionProbeViewController(tallHeader, largeText)`. Swift calls it through `TemporaryProfileMotionProbeControllerKt`. The hosted tests put that controller in a test UIWindow and restore the previous key window afterward. The UI tests launch the application with `--temporary-profile-motion-probe`, which selects the standalone probe before any app/account UI. Optional launch arguments are `--probe-tall-header` and `--probe-large-text`. No Accounts or AppShell files changed.

The fixed spacer variants produce normal and tall naturally measured headers. The large-text variant uses a Compose font scale of 1.8. Posts has 18 tall body rows; Media is empty; Featured and About are short; Activity has filters and tall rows. Loading replaces the selected body's rows. The refresh count increments only from the actual Material3 PullToRefreshBox callback. `refreshAt` records collapse, body index, and body offset at that callback.

The top controls labeled Top, End, and Body issue real BringIntoViewRequester requests for actions. Clip places the first body action partly below the outer viewport through the parent controller. The hosted focus test then posts the public UIKit element-focused notification with that actual accessibility element, and requires the native Compose accessibility bridge to move it fully into view.

## Run the native gates

Root owns native execution. Use the worktree project `iosApp/QuanToot.xcodeproj`, scheme `QuanToot`, and the authorized simulator. The project already includes both temporary Swift files in their respective targets. The complete suite selectors are:

```text
QuanTootTests/TemporaryProfileAccessibilityProbeTests
QuanTootUITests/TemporaryProfileMotionProbeTests
```

The exact hosted test selectors are:

```text
QuanTootTests/TemporaryProfileAccessibilityProbeTests/testNativeAccessibilityScrollMovesEmptyHeaderDownAndUp
QuanTootTests/TemporaryProfileAccessibilityProbeTests/testNativeAccessibilityScrollCrossesIntoBodyAndCanActivateActions
QuanTootTests/TemporaryProfileAccessibilityProbeTests/testNativeActivationBringsTallHeaderActionsIntoViewAtLargeText
QuanTootTests/TemporaryProfileAccessibilityProbeTests/testClippedBodyIsNotAnAccessibleActionUntilRevealed
```

The exact UI test selectors are:

```text
QuanTootUITests/TemporaryProfileMotionProbeTests/testHeaderOriginFlingContinuesIntoBodyAndReverses
QuanTootUITests/TemporaryProfileMotionProbeTests/testEmptyBodyScrollAndAllCategorySwitchesKeepTheRowStable
QuanTootUITests/TemporaryProfileMotionProbeTests/testLoadingAndActivityFilterSwitchesKeepTheHeaderStable
QuanTootUITests/TemporaryProfileMotionProbeTests/testRefreshWaitsForBothHeaderAndBodyToReachTheBeginning
QuanTootUITests/TemporaryProfileMotionProbeTests/testClippedActionsAndBringIntoView
QuanTootUITests/TemporaryProfileMotionProbeTests/testTallHeaderAndLargeTextRemainUncapped
```

Keep the screenshots, metrics, accessibility tree attachments, and native result bundle in the root audit before removing the temporary files. A test setup failure is not a motion failure or a pass. The touch distances and Swift bridge signatures have not been validated by a native build in this worker.

## Verified locally

Both commands ran sequentially in this worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home` and exited 0:

```sh
./gradlew :shared:testAndroidHostTest --tests com.quanquan.quantoot.ui.ProfileScrollGeometryTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The host XML reports seven tests, zero skips, zero failures, and zero errors at `shared/build/test-results/testAndroidHostTest/TEST-com.quanquan.quantoot.ui.ProfileScrollGeometryTest.xml`. They check empty uncapped header travel, forward and reverse boundary routing, body replacement geometry, header remeasurement, clipped body visibility, and reveal distances beneath the category row. The final compile included the complete Kotlin probe. `git diff --check` passed, and `plutil -lint` accepted the temporary project file. Swift tests were inspected but neither compiled nor executed.

## Pinned API evidence and limits

The downloaded primary sources are from Maven Central:

- [Compose foundation 1.12.2 sources](https://repo.maven.apache.org/maven2/org/jetbrains/compose/foundation/foundation/1.12.2/foundation-1.12.2-sources.jar), SHA-256 `bd7ac562e7284bcc15ff5c9e0bb9c7796217dceee4462638e15d00f1dcc7c73e`.
- [Compose UI 1.12.2 sources](https://repo.maven.apache.org/maven2/org/jetbrains/compose/ui/ui/1.12.2/ui-1.12.2-sources.jar), SHA-256 `24ffda111c0b65df33c746265cbf6afef7c8ad9b285cf037e1b5a56e3d4fec2b`.

The jars and extracted sources are under the ignored `build/profile-motion-probe/apis/` directory. `ScrollableArea.kt` confirms positive forward-reading deltas and normal fling delegation. `AbstractScrollableNode.kt` installs ScrollBy and ScrollByOffset. `LazyList.kt` still installs its focus responder with `userScrollEnabled=false`. The core supplies a zero-distance BringIntoViewSpec only to that list and restores the original spec inside each item, preserving nested horizontal focus behavior. Item and header requests use the same parent mutation scope that acquires the body's scroll scope. `LazyListScrollScope.kt` supplies the public in-scope snap API used when revealing a hidden header action. No per-delta coroutine or raw body delta dispatch is used.

`Accessibility.ios.kt` and `SemanticsNodeUtils.ios.kt` confirm native accessibilityScroll calls ancestor ScrollBy, while native focused-element notifications use ScrollByOffset. This version's ComposeContainerConfiguration has no accessibilitySyncOptions or test-mode setting; the mediator is enabled by the container's focus state. The hosted test therefore waits for the actual public accessibility tree instead of using guessed or private APIs. UIKit's local simulator SDK headers expose NSObject accessibilityScroll, accessibilityActivate, accessibilityElementCount, accessibilityElementAtIndex, accessibilityElements, and the element-focused notification/key. No private UIKit selector is used.

The core only handles composed bring-into-view targets. Disabling LazyColumn gestures also disables its beyond-bounds modifier. Hardware keyboard traversal into uncomposed rows is not established by these tests. Native VoiceOver page scrolling and native focus of a partially clipped composed action remain explicit root gates. Large text in this probe uses Compose density, so root should also confirm its desired system Dynamic Type configuration during final integration.

There is no FeedPager, cache, restore, pagination trigger, route-retention, or account integration in this unit. The pure body visibility function intersects the list's own top with the outer viewport, but actual live-feed pagination must use it during the later adapter integration. Only the current selected body exists; selecting a category or Activity filter cancels current motion and installs a fresh body state while retaining header collapse.

## Remove the temporary work

After preserving native evidence, reverse the six-file patch with `git apply -R temporary-probe.patch`, or discard those exact six temporary paths. Keep the four files from the core commit. Also remove this temporary README and patch when they are no longer needed. The ignored `.audit/profile-motion-probe.todo.md`, `build/profile-motion-probe/apis/`, generated Gradle outputs, and `local.properties` are local support files. The only copied local property was `sdk.dir`.

The Model the Domain principle shaped the typed selection/body attachment and independent header geometry. Prove It Works keeps native gates open despite successful host tests and compilation. Independent review remains root-owned; this worker spawned no agents.

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quanquan.quantoot

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import com.quanquan.quantoot.platform.IosPlatformServices
import com.quanquan.quantoot.ui.*
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController

fun MainViewController(
    platform: IosPlatformServices,
    navigation: NativeNavigation?,
    createMediaArrangement: ((UIViewController, UIViewController, String, () -> Unit) -> UIViewController)?,
) = ComposeUIViewController {
    val window by platform.adaptiveWindow.collectAsState()
    val media = remember(platform, createMediaArrangement) {
        createMediaArrangement?.let { IosMediaArrangement(platform, it) }
    }
    CompositionLocalProvider(LocalNativeNavigation provides navigation, LocalMediaArrangement provides media) {
        QuanTootApp(platform, window)
    }
}

private class IosMediaArrangement(
    private val platform: IosPlatformServices,
    private val create: (UIViewController, UIViewController, String, () -> Unit) -> UIViewController,
) : MediaArrangement {
    @Composable
    override fun Present(
        onDismiss: () -> Unit,
        player: @Composable (() -> Unit) -> Unit,
        controls: @Composable () -> Unit,
    ) {
        val currentPlayer by rememberUpdatedState(player)
        val currentControls by rememberUpdatedState(controls)
        val dismiss by rememberUpdatedState(onDismiss)
        val strings = LocalStrings.current
        DisposableEffect(strings) {
            var presented: UIViewController? = null
            // A full-screen presentation can pause the presenting Compose hierarchy.
            val close: () -> Unit = {
                presented?.let { gallery ->
                    presented = null
                    gallery.dismissViewControllerAnimated(true) { dismiss() }
                }
            }
            val playerController = ComposeUIViewController {
                CompositionLocalProvider(LocalStrings provides strings) { QuanTheme { currentPlayer(close) } }
            }
            val controlsController = ComposeUIViewController {
                CompositionLocalProvider(LocalStrings provides strings) { QuanTheme { currentControls() } }
            }
            val controller = create(playerController, controlsController, strings.text("mastodon_close"), close)
            presented = controller
            controller.modalPresentationStyle = UIModalPresentationFullScreen
            platform.presenter().presentViewController(controller, animated = true, completion = null)
            onDispose {
                val gallery = presented
                presented = null
                if (gallery?.presentingViewController != null) gallery.dismissViewControllerAnimated(true, null)
            }
        }
    }
}

package com.quanquan.quantoot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

internal enum class GalleryGestureOwner { Undecided, Pager, Transform, Dismiss }

internal data class GalleryDismissAnimation(val generation: Int, val from: Float, val target: Float)

internal class GalleryGestureState {
    var owner = GalleryGestureOwner.Undecided
        private set
    var dismissOffset by mutableFloatStateOf(0f)
        private set
    private var movement = Offset.Zero
    private var generation = 0

    fun begin(): Int {
        cancel()
        return generation
    }

    fun isCurrent(generation: Int) = this.generation == generation

    fun move(pan: Offset, pointers: Int, zoomed: Boolean, touchSlop: Float): GalleryGestureOwner {
        if (pointers > 1 || (zoomed && owner != GalleryGestureOwner.Undecided)) {
            owner = GalleryGestureOwner.Transform
            dismissOffset = 0f
        }
        when (owner) {
            GalleryGestureOwner.Undecided -> {
                movement += pan
                if (movement.getDistance() > touchSlop) {
                    owner = when {
                        zoomed -> GalleryGestureOwner.Transform
                        abs(movement.y) > abs(movement.x) -> GalleryGestureOwner.Dismiss
                        else -> GalleryGestureOwner.Pager
                    }
                    if (owner == GalleryGestureOwner.Dismiss) dismissOffset = movement.y
                }
            }
            GalleryGestureOwner.Dismiss -> dismissOffset += pan.y
            else -> Unit
        }
        return owner
    }

    fun release(viewportHeight: Float, distanceCap: Float, cancelled: Boolean = false): GalleryDismissAnimation? {
        val dismiss = !cancelled && owner == GalleryGestureOwner.Dismiss && viewportHeight > 0f &&
            abs(dismissOffset) >= min(viewportHeight * 0.2f, distanceCap)
        val target = if (dismiss) sign(dismissOffset) * max(viewportHeight, abs(dismissOffset)) else 0f
        generation += 1
        owner = GalleryGestureOwner.Undecided
        return if (dismissOffset == 0f) null else GalleryDismissAnimation(generation, dismissOffset, target)
    }

    fun animate(animation: GalleryDismissAnimation, value: Float) {
        if (isCurrent(animation.generation)) dismissOffset = value
    }

    fun complete(animation: GalleryDismissAnimation): Boolean {
        if (!isCurrent(animation.generation)) return false
        generation += 1
        dismissOffset = animation.target
        return animation.target != 0f
    }

    fun cancel() {
        generation += 1
        owner = GalleryGestureOwner.Undecided
        movement = Offset.Zero
        dismissOffset = 0f
    }
}

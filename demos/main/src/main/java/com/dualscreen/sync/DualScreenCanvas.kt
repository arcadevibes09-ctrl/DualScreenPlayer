package com.dualscreen.sync

import android.content.Context
import android.widget.FrameLayout
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView

/**
 * Hosts an oversized PlayerView representing the FULL combined video canvas (both phones'
 * screens put together), positioned with a negative offset so only THIS phone's slice is
 * visible. clipChildren=true on the parent is what actually crops the picture — no matrix
 * math on the video surface needed.
 */
class DualScreenCanvas(context: Context) : FrameLayout(context) {
    val playerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    init {
        clipChildren = true
        clipToPadding = true
        addView(playerView)
    }

    /** Call once the arrangement is known, and again whenever the video's intrinsic size changes. */
    fun applyArrangement(arrangement: Arrangement, videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return
        val dm = context.resources.displayMetrics
        val myDpiX = dm.xdpi
        val myDpiY = dm.ydpi

        // "Cover" scale: fill the whole combined canvas, cropping any excess (like centerCrop).
        val canvasAspect = arrangement.canvasWidthMm / arrangement.canvasHeightMm
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val scaledWidthMm: Float
        val scaledHeightMm: Float
        if (videoAspect > canvasAspect) {
            scaledHeightMm = arrangement.canvasHeightMm
            scaledWidthMm = scaledHeightMm * videoAspect
        } else {
            scaledWidthMm = arrangement.canvasWidthMm
            scaledHeightMm = scaledWidthMm / videoAspect
        }
        val centerOffsetXMm = (arrangement.canvasWidthMm - scaledWidthMm) / 2f
        val centerOffsetYMm = (arrangement.canvasHeightMm - scaledHeightMm) / 2f

        // Convert the scaled video's mm size into THIS device's own pixels via its own DPI —
        // this is the step that keeps two different phone models aligned correctly.
        val bigWidthPx = (scaledWidthMm / 25.4f * myDpiX).toInt()
        val bigHeightPx = (scaledHeightMm / 25.4f * myDpiY).toInt()
        playerView.layoutParams = LayoutParams(bigWidthPx, bigHeightPx)

        val myOffsetXMm = centerOffsetXMm - arrangement.local.offsetXMm
        val myOffsetYMm = centerOffsetYMm - arrangement.local.offsetYMm
        playerView.translationX = myOffsetXMm / 25.4f * myDpiX
        playerView.translationY = myOffsetYMm / 25.4f * myDpiY
        requestLayout()
    }
}

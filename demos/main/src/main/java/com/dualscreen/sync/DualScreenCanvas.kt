package com.dualscreen.sync

import android.content.Context
import android.widget.FrameLayout
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView

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

    fun applyArrangement(arrangement: Arrangement, videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return
        val dm = context.resources.displayMetrics
        val myDpiX = dm.xdpi
        val myDpiY = dm.ydpi

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

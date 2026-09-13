package com.dualscreen.sync

/** Which side THIS device sits on relative to the other device, chosen by the user during pairing. */
enum class Side { LEFT, RIGHT, TOP, BOTTOM }

/** A device's screen size in millimetres (not pixels) — the only unit that's directly
 *  comparable across two different phone models with different pixel densities. */
data class DeviceScreenInfo(
    val widthMm: Float,
    val heightMm: Float
) {
    companion object {
        fun current(context: android.content.Context): DeviceScreenInfo {
            val dm = context.resources.displayMetrics
            val widthMm = dm.widthPixels / dm.xdpi * 25.4f
            val heightMm = dm.heightPixels / dm.ydpi * 25.4f
            return DeviceScreenInfo(widthMm, heightMm)
        }
    }
}

/** Where a device sits inside the shared "combined canvas" (all measurements in mm). */
data class DevicePlacement(
    val offsetXMm: Float,
    val offsetYMm: Float,
    val screen: DeviceScreenInfo
)

/** Full description of the two-phone canvas: overall size + where each phone sits in it. */
data class Arrangement(
    val canvasWidthMm: Float,
    val canvasHeightMm: Float,
    val local: DevicePlacement,
    val remote: DevicePlacement
) {
    companion object {
        /**
         * Builds the combined canvas from both devices' physical sizes and the side the
         * PEER device is on, from THIS device's point of view (peerSide = RIGHT means
         * "the other phone is to my right").
         */
        fun build(local: DeviceScreenInfo, remote: DeviceScreenInfo, peerSide: Side): Arrangement =
            when (peerSide) {
                Side.RIGHT -> Arrangement(
                    canvasWidthMm = local.widthMm + remote.widthMm,
                    canvasHeightMm = maxOf(local.heightMm, remote.heightMm),
                    local = DevicePlacement(0f, 0f, local),
                    remote = DevicePlacement(local.widthMm, 0f, remote)
                )
                Side.LEFT -> Arrangement(
                    canvasWidthMm = local.widthMm + remote.widthMm,
                    canvasHeightMm = maxOf(local.heightMm, remote.heightMm),
                    local = DevicePlacement(remote.widthMm, 0f, local),
                    remote = DevicePlacement(0f, 0f, remote)
                )
                Side.BOTTOM -> Arrangement(
                    canvasWidthMm = maxOf(local.widthMm, remote.widthMm),
                    canvasHeightMm = local.heightMm + remote.heightMm,
                    local = DevicePlacement(0f, remote.heightMm, local),
                    remote = DevicePlacement(0f, 0f, remote)
                )
                Side.TOP -> Arrangement(
                    canvasWidthMm = maxOf(local.widthMm, remote.widthMm),
                    canvasHeightMm = local.heightMm + remote.heightMm,
                    local = DevicePlacement(0f, 0f, local),
                    remote = DevicePlacement(0f, local.heightMm, remote)
                )
            }
    }
}

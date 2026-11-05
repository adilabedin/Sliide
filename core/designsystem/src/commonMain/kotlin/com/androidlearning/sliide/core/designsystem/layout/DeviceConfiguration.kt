package com.androidlearning.sliide.core.designsystem.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/** The [DeviceConfiguration] of the current window. */
@Composable
fun currentDeviceConfiguration(): DeviceConfiguration = DeviceConfiguration.fromWindowSizeClass(currentWindowAdaptiveInfo().windowSizeClass)

/** The kind of window the app is drawn in, for layouts whose structure (not just size) changes with it. */
enum class DeviceConfiguration {
    MOBILE_PORTRAIT,
    MOBILE_LANDSCAPE,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    DESKTOP,
    ;

    companion object {
        /**
         * Every window falls into exactly one bucket: a narrow window is a phone held upright, whatever its height,
         * and a short one that is wider is a phone on its side. Only then do the medium and expanded widths split
         * tablets from desktops, so a small phone in landscape or a half-open foldable is never taken for a desktop.
         */
        fun fromWindowSizeClass(windowSizeClass: WindowSizeClass): DeviceConfiguration =
            with(windowSizeClass) {
                when {
                    minWidthDp < WIDTH_DP_MEDIUM_LOWER_BOUND -> MOBILE_PORTRAIT
                    minHeightDp < HEIGHT_DP_MEDIUM_LOWER_BOUND -> MOBILE_LANDSCAPE
                    minWidthDp < WIDTH_DP_EXPANDED_LOWER_BOUND -> TABLET_PORTRAIT
                    minHeightDp < HEIGHT_DP_EXPANDED_LOWER_BOUND -> TABLET_LANDSCAPE
                    else -> DESKTOP
                }
            }
    }
}

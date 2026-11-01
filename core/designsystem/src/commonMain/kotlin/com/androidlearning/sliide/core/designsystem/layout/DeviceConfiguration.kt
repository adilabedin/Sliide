package com.androidlearning.sliide.core.designsystem.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable

/**
 * Window width buckets that change the structure of a screen (spec: adaptive-layout).
 * Compact: under 600dp (phone portrait). Medium: 600dp to under 840dp (phone landscape, small tablet).
 * Expanded: 840dp and wider (tablet landscape, desktop).
 */
enum class DeviceConfiguration {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    companion object {
        const val MEDIUM_MIN_WIDTH_DP = 600
        const val EXPANDED_MIN_WIDTH_DP = 840

        fun fromWidthDp(widthDp: Int): DeviceConfiguration =
            when {
                widthDp >= EXPANDED_MIN_WIDTH_DP -> EXPANDED
                widthDp >= MEDIUM_MIN_WIDTH_DP -> MEDIUM
                else -> COMPACT
            }
    }
}

/** The [DeviceConfiguration] of the current window. */
@Composable
fun currentDeviceConfiguration(): DeviceConfiguration =
    DeviceConfiguration.fromWidthDp(currentWindowAdaptiveInfo().windowSizeClass.minWidthDp)

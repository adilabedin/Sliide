package com.androidlearning.sliide.core.designsystem.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DeviceConfigurationTest {
    @Test
    fun phonePortraitIsCompact() {
        assertThat(DeviceConfiguration.fromWidthDp(411)).isEqualTo(DeviceConfiguration.COMPACT)
        assertThat(DeviceConfiguration.fromWidthDp(599)).isEqualTo(DeviceConfiguration.COMPACT)
    }

    @Test
    fun phoneLandscapeIsMedium() {
        assertThat(DeviceConfiguration.fromWidthDp(600)).isEqualTo(DeviceConfiguration.MEDIUM)
        assertThat(DeviceConfiguration.fromWidthDp(731)).isEqualTo(DeviceConfiguration.MEDIUM)
        assertThat(DeviceConfiguration.fromWidthDp(839)).isEqualTo(DeviceConfiguration.MEDIUM)
    }

    @Test
    fun tabletLandscapeIsExpanded() {
        assertThat(DeviceConfiguration.fromWidthDp(840)).isEqualTo(DeviceConfiguration.EXPANDED)
        assertThat(DeviceConfiguration.fromWidthDp(1280)).isEqualTo(DeviceConfiguration.EXPANDED)
    }
}

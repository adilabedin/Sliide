package com.androidlearning.sliide.core.designsystem.layout

import androidx.window.core.layout.WindowSizeClass
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DeviceConfigurationTest {
    // Window size classes hold the lower bound of each bucket, so the real dp sizes are noted alongside.
    private fun configurationOf(
        widthDp: Int,
        heightDp: Int,
    ): DeviceConfiguration = DeviceConfiguration.fromWindowSizeClass(WindowSizeClass.compute(widthDp.toFloat(), heightDp.toFloat()))

    @Test
    fun aPhoneHeldUprightIsMobilePortrait() {
        assertThat(configurationOf(widthDp = 411, heightDp = 891)).isEqualTo(DeviceConfiguration.MOBILE_PORTRAIT)
    }

    @Test
    fun aNarrowAndShortWindowIsStillMobilePortrait() {
        assertThat(configurationOf(widthDp = 400, heightDp = 300)).isEqualTo(DeviceConfiguration.MOBILE_PORTRAIT)
    }

    @Test
    fun aLargePhoneOnItsSideIsMobileLandscape() {
        assertThat(configurationOf(widthDp = 891, heightDp = 411)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun aSmallPhoneOnItsSideIsMobileLandscapeRatherThanDesktop() {
        assertThat(configurationOf(widthDp = 740, heightDp = 360)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun aTabletHeldUprightIsTabletPortrait() {
        assertThat(configurationOf(widthDp = 800, heightDp = 1280)).isEqualTo(DeviceConfiguration.TABLET_PORTRAIT)
    }

    @Test
    fun anUnfoldedFoldableIsTabletPortraitRatherThanDesktop() {
        assertThat(configurationOf(widthDp = 673, heightDp = 841)).isEqualTo(DeviceConfiguration.TABLET_PORTRAIT)
    }

    @Test
    fun aTabletOnItsSideIsTabletLandscape() {
        assertThat(configurationOf(widthDp = 1280, heightDp = 800)).isEqualTo(DeviceConfiguration.TABLET_LANDSCAPE)
    }

    @Test
    fun aLargeWindowIsDesktop() {
        assertThat(configurationOf(widthDp = 1440, heightDp = 960)).isEqualTo(DeviceConfiguration.DESKTOP)
    }

    @Test
    fun onlyAPhoneHeldUprightShowsOnePaneAtATime() {
        DeviceConfiguration.entries.forEach { configuration ->
            assertThat(configuration.showsListDetailOnePaneAtATime)
                .isEqualTo(configuration == DeviceConfiguration.MOBILE_PORTRAIT)
        }
    }
}

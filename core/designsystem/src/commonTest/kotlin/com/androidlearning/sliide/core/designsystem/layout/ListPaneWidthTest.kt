package com.androidlearning.sliide.core.designsystem.layout

import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ListPaneWidthTest {
    @Test
    fun aSmallLandscapePhoneKeepsTheMinimumWidth() {
        assertThat(listPaneWidth(600.dp)).isEqualTo(280.dp)
    }

    @Test
    fun aMidSizeWindowGetsFortyPercent() {
        assertThat(listPaneWidth(900.dp)).isEqualTo(360.dp)
    }

    @Test
    fun aWideWindowIsCappedAtTheMaximumWidth() {
        assertThat(listPaneWidth(1440.dp)).isEqualTo(400.dp)
    }
}

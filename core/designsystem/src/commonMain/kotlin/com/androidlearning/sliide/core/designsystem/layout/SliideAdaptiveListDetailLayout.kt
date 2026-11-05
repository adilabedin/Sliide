package com.androidlearning.sliide.core.designsystem.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme

/** Whether [SliideAdaptiveListDetailLayout] shows the list and the detail one at a time rather than side by side. */
val DeviceConfiguration.showsListDetailOnePaneAtATime: Boolean
    get() =
        when (this) {
            DeviceConfiguration.MOBILE_PORTRAIT -> true

            DeviceConfiguration.MOBILE_LANDSCAPE,
            DeviceConfiguration.TABLET_PORTRAIT,
            DeviceConfiguration.TABLET_LANDSCAPE,
            DeviceConfiguration.DESKTOP,
            -> false
        }

/**
 * A list and the detail of the item picked from it, [selectedItem] (spec: adaptive-layout). A phone held upright
 * shows one at a time: the detail slides in over the list while an item is selected. Anything wider keeps the list on
 * the start side, [listPaneWidth] wide, and the detail beside it, with [emptyDetail] standing in until an item is
 * selected.
 *
 * Both arrangements only animate between "nothing selected" and "something selected": picking another item updates the
 * detail in place, so a detail backed by one shared ViewModel is never composed twice at once.
 *
 * Each pane is usually a full screen with its own `Scaffold`, so the list's scroll position is kept while it is off
 * screen or moves between the two arrangements, and each pane only pads for the system bars along its own edges.
 */
@Composable
fun <T : Any> SliideAdaptiveListDetailLayout(
    selectedItem: T?,
    list: @Composable () -> Unit,
    detail: @Composable (T) -> Unit,
    emptyDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    configuration: DeviceConfiguration = currentDeviceConfiguration(),
) {
    val stateHolder = rememberSaveableStateHolder()
    val savedList: @Composable () -> Unit = { stateHolder.SaveableStateProvider(LIST_STATE_KEY) { list() } }

    if (configuration.showsListDetailOnePaneAtATime) {
        AnimatedContent(
            targetState = selectedItem,
            modifier = modifier.fillMaxSize(),
            contentKey = { it != null },
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
                }
            },
            label = "listDetailOnePane",
        ) { item ->
            if (item == null) savedList() else detail(item)
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val listWidth = listPaneWidth(maxWidth)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .width(listWidth)
                            .fillMaxHeight()
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
                ) {
                    savedList()
                }
                VerticalDivider()
                AnimatedContent(
                    targetState = selectedItem,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    contentKey = { it != null },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "listDetailTwoPane",
                ) { item ->
                    if (item == null) emptyDetail() else detail(item)
                }
            }
        }
    }
}

/**
 * The list's width beside the detail: 40% of [windowWidth], kept between 280dp (cards stay readable on a 600dp
 * landscape phone) and 400dp (rows don't stretch across a desktop-wide window).
 */
fun listPaneWidth(windowWidth: Dp): Dp = (windowWidth * LIST_PANE_FRACTION).coerceIn(LIST_PANE_MIN_WIDTH, LIST_PANE_MAX_WIDTH)

private const val LIST_STATE_KEY = "list"
private const val LIST_PANE_FRACTION = 0.4f
private val LIST_PANE_MIN_WIDTH = 280.dp
private val LIST_PANE_MAX_WIDTH = 400.dp

@Composable
private fun PreviewPane(text: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun SliideAdaptiveListDetailLayoutMobilePortraitPreview() {
    SliideTheme {
        SliideAdaptiveListDetailLayout(
            selectedItem = null,
            list = { PreviewPane("List") },
            detail = { item: String -> PreviewPane(item) },
            emptyDetail = { PreviewPane("Nothing selected") },
            configuration = DeviceConfiguration.MOBILE_PORTRAIT,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SliideAdaptiveListDetailLayoutTabletLandscapePreview() {
    SliideTheme {
        SliideAdaptiveListDetailLayout(
            selectedItem = "Detail",
            list = { PreviewPane("List") },
            detail = { item: String -> PreviewPane(item) },
            emptyDetail = { PreviewPane("Nothing selected") },
            configuration = DeviceConfiguration.TABLET_LANDSCAPE,
        )
    }
}

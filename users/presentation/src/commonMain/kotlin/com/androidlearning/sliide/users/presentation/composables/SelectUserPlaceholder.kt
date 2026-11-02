package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.component.FullScreenMessage
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_select_body
import com.androidlearning.sliide.users.presentation.generated.resources.profile_select_title
import org.jetbrains.compose.resources.stringResource

/** The detail pane on two-pane layouts when there is no user to show (spec: adaptive-layout, "Nothing selected"). */
@Composable
internal fun SelectUserPlaceholder(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box {
            FullScreenMessage(
                icon = Icons.Filled.Person,
                title = stringResource(Res.string.profile_select_title),
                body = stringResource(Res.string.profile_select_body),
            )
        }
    }
}

@Preview(widthDp = 480, heightDp = 720)
@Composable
private fun SelectUserPlaceholderLightPreview() {
    SliideTheme(darkTheme = false) { SelectUserPlaceholder() }
}

@Preview(widthDp = 480, heightDp = 720)
@Composable
private fun SelectUserPlaceholderDarkPreview() {
    SliideTheme(darkTheme = true) { SelectUserPlaceholder() }
}

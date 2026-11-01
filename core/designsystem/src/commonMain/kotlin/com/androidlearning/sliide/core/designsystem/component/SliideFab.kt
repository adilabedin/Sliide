package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme

/**
 * The primary floating action: ink container, soft elevation, icon plus [text]. When [expanded] is false (the list is
 * scrolled) it collapses to the icon alone; [text] stays its accessible label either way.
 */
@Composable
fun SliideFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    icon: ImageVector = Icons.Filled.Add,
) {
    ExtendedFloatingActionButton(
        text = { Text(text = text, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        modifier = modifier,
        shape = SliideShapes.large,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
    )
}

@Composable
private fun FabSample(expanded: Boolean) {
    Surface {
        Box(modifier = Modifier.padding(SliideSpacing.xl)) {
            SliideFab(text = "Add user", onClick = {}, expanded = expanded)
        }
    }
}

@Preview
@Composable
private fun SliideFabExpandedLightPreview() {
    SliideTheme(darkTheme = false) { FabSample(expanded = true) }
}

@Preview
@Composable
private fun SliideFabCollapsedLightPreview() {
    SliideTheme(darkTheme = false) { FabSample(expanded = false) }
}

@Preview
@Composable
private fun SliideFabExpandedDarkPreview() {
    SliideTheme(darkTheme = true) { FabSample(expanded = true) }
}

@Preview
@Composable
private fun SliideFabCollapsedDarkPreview() {
    SliideTheme(darkTheme = true) { FabSample(expanded = false) }
}

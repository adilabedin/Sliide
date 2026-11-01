package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors

/** A full-width filled button in the refined destructive red, with a bin icon. Used only for destructive actions. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = SliideShapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.extendedColors.destructive,
                contentColor = MaterialTheme.extendedColors.onDestructive,
            ),
    ) {
        Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(SliideSpacing.s))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DestructiveSample(enabled: Boolean) {
    Surface {
        Box(modifier = Modifier.padding(SliideSpacing.l)) {
            DestructiveButton(text = "Delete", onClick = {}, enabled = enabled)
        }
    }
}

@Preview
@Composable
private fun DestructiveButtonLightPreview() {
    SliideTheme(darkTheme = false) { DestructiveSample(enabled = true) }
}

@Preview
@Composable
private fun DestructiveButtonDisabledLightPreview() {
    SliideTheme(darkTheme = false) { DestructiveSample(enabled = false) }
}

@Preview
@Composable
private fun DestructiveButtonDarkPreview() {
    SliideTheme(darkTheme = true) { DestructiveSample(enabled = true) }
}

@Preview
@Composable
private fun DestructiveButtonDisabledDarkPreview() {
    SliideTheme(darkTheme = true) { DestructiveSample(enabled = false) }
}

package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun ThemeSample() {
    Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sliide users", style = MaterialTheme.typography.titleLarge)
            Text("jane.doe@example.com", style = MaterialTheme.typography.bodyMedium)
            Swatch("primary", MaterialTheme.colorScheme.primary)
            Swatch("secondary", MaterialTheme.colorScheme.secondary)
            Swatch("tertiary", MaterialTheme.colorScheme.tertiary)
            Swatch("error", MaterialTheme.colorScheme.error)
            Swatch("surfaceContainerLow (card)", MaterialTheme.colorScheme.surfaceContainerLow)
            Swatch("inverseSurface", MaterialTheme.colorScheme.inverseSurface)
            Swatch("destructive", MaterialTheme.extendedColors.destructive)
            Swatch("inverseAccent", MaterialTheme.extendedColors.inverseAccent)
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(color, RoundedCornerShape(6.dp)),
        )
        Text(
            text = name,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview
@Composable
private fun SliideThemeLightPreview() {
    SliideTheme(darkTheme = false) { ThemeSample() }
}

@Preview
@Composable
private fun SliideThemeDarkPreview() {
    SliideTheme(darkTheme = true) { ThemeSample() }
}

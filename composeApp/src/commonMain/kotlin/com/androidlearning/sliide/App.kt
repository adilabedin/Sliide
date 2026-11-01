package com.androidlearning.sliide

import androidx.compose.runtime.Composable
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.users.UsersRoot

/** Shared app shell for Android and iOS. Koin is started by the platform entry point, not here. */
@Composable
fun App() {
    SliideTheme {
        UsersRoot()
    }
}

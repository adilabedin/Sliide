package com.androidlearning.sliide

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// PascalCase on purpose: iosApp's ContentView.swift calls `MainViewControllerKt.MainViewController()`.
@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

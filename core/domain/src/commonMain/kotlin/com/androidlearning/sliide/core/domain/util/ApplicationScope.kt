package com.androidlearning.sliide.core.domain.util

import kotlinx.coroutines.CoroutineScope

/**
 * The process-wide scope the application creates and hands to Koin (spec: operation-lifetime). Work launched in it
 * outlives any screen or ViewModel, such as a delete the user confirmed just before leaving. Its own type so any layer
 * can inject it without a qualifier; tests wrap a test scope.
 */
class ApplicationScope(
    scope: CoroutineScope,
) : CoroutineScope by scope

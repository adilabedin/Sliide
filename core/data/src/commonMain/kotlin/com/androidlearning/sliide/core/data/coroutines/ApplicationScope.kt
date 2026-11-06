package com.androidlearning.sliide.core.data.coroutines

import kotlinx.coroutines.CoroutineScope

/**
 * The process-wide scope the application creates and hands to Koin (spec: operation-lifetime). Work launched in it
 * outlives any screen or ViewModel, such as a delete the user confirmed just before leaving. Only the data layer runs
 * work in it: repositories start their writes here and callers just await them. Its own type so it can be injected
 * without a qualifier; tests wrap a test scope.
 */
class ApplicationScope(
    scope: CoroutineScope,
) : CoroutineScope by scope

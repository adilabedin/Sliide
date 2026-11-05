package com.androidlearning.sliide.users.presentation.navigation

import kotlinx.serialization.Serializable

/** The users feature as a whole, for the app's `NavHost`. */
@Serializable
data object UsersGraphRoute

/** The feed and the open profile: one destination, laid out side by side or one at a time (spec: adaptive-layout). */
@Serializable
data object UsersRoute

/** The full-screen add-user form (spec: user-profile). */
@Serializable
data object CreateUserRoute

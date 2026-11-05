package com.androidlearning.sliide.users.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.listdetail.UsersListDetailRoot
import com.androidlearning.sliide.users.presentation.profile.ProfileKey
import com.androidlearning.sliide.users.presentation.profile.ProfileRoot
import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Where the add-user screen leaves its [WriteStatus] on the feed's entry. Kept by name, so it survives process death. */
private const val KEY_CREATED_STATUS = "created_status"

fun NavGraphBuilder.usersGraph(navController: NavController) {
    navigation<UsersGraphRoute>(startDestination = UsersRoute) {
        composable<UsersRoute> { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val createdStatus by savedStateHandle
                .getStateFlow<String?>(KEY_CREATED_STATUS, null)
                .collectAsStateWithLifecycle()
            UsersListDetailRoot(
                onAddUserClick = { navController.navigate(CreateUserRoute) { launchSingleTop = true } },
                createdStatus = createdStatus?.let(WriteStatus::valueOf),
                onCreatedStatusShown = { savedStateHandle[KEY_CREATED_STATUS] = null },
            )
        }
        composable<CreateUserRoute> {
            CreateUserRoot(
                onNavigateBack = { navController.navigateUp() },
                onCreated = { status ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_CREATED_STATUS, status.name)
                    navController.navigateUp()
                },
            )
        }
    }
}

/**
 * The add-user screen: the profile form in Create mode, full screen at every window size. Its ViewModel belongs to this
 * destination, so every visit starts from an empty form.
 */
@Composable
private fun CreateUserRoot(
    onNavigateBack: () -> Unit,
    onCreated: (WriteStatus) -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    ProfileRoot(
        viewModel = viewModel,
        profileKey = ProfileKey.CREATE,
        showBackButton = true,
        onClose = { onNavigateBack() },
        onCreated = onCreated,
    )
}

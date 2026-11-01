package com.androidlearning.sliide.users.presentation.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.users_empty_body
import com.androidlearning.sliide.users.presentation.generated.resources.users_empty_title
import com.androidlearning.sliide.users.presentation.generated.resources.users_loading
import com.androidlearning.sliide.users.presentation.generated.resources.users_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UsersRoot(viewModel: UsersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UsersScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    state: UsersState,
    onAction: (UsersAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.users_title)) })
        },
    ) { innerPadding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        when {
            state.isLoading -> {
                val loadingDescription = stringResource(Res.string.users_loading)
                Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = loadingDescription },
                    )
                }
            }

            state.users.isEmpty() -> {
                Column(
                    modifier = contentModifier.padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.users_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.users_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = state.users, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = { Text(user.name) },
                            supportingContent = { Text(user.email) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val previewUsers =
    listOf(
        UserUi(id = 7_401_001, name = "Jane Doe", email = "jane.doe@example.com"),
        UserUi(id = 7_401_002, name = "Rahul Sharma", email = "rahul.sharma@example.in"),
        UserUi(id = 7_401_003, name = "Amélie Laurent", email = "amelie.laurent@example.fr"),
    )

@Preview
@Composable
private fun UsersScreenLoadingLightPreview() {
    SliideTheme(darkTheme = false) {
        UsersScreen(state = UsersState(isLoading = true), onAction = {})
    }
}

@Preview
@Composable
private fun UsersScreenLoadingDarkPreview() {
    SliideTheme(darkTheme = true) {
        UsersScreen(state = UsersState(isLoading = true), onAction = {})
    }
}

@Preview
@Composable
private fun UsersScreenEmptyLightPreview() {
    SliideTheme(darkTheme = false) {
        UsersScreen(state = UsersState(isLoading = false), onAction = {})
    }
}

@Preview
@Composable
private fun UsersScreenEmptyDarkPreview() {
    SliideTheme(darkTheme = true) {
        UsersScreen(state = UsersState(isLoading = false), onAction = {})
    }
}

@Preview
@Composable
private fun UsersScreenPopulatedLightPreview() {
    SliideTheme(darkTheme = false) {
        UsersScreen(state = UsersState(users = previewUsers, isLoading = false), onAction = {})
    }
}

@Preview
@Composable
private fun UsersScreenPopulatedDarkPreview() {
    SliideTheme(darkTheme = true) {
        UsersScreen(state = UsersState(users = previewUsers, isLoading = false), onAction = {})
    }
}

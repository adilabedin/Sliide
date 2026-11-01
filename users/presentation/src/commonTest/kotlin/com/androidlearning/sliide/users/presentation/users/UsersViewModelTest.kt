package com.androidlearning.sliide.users.presentation.users

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {
    private lateinit var repository: FakeUserRepository
    private lateinit var viewModel: UsersViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeUserRepository()
        viewModel = UsersViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() =
        runTest {
            viewModel.state.test {
                assertThat(awaitItem()).isEqualTo(UsersState(users = emptyList(), isLoading = true))
            }
        }

    @Test
    fun usersFromRepositoryAreMappedIntoState() =
        runTest {
            viewModel.state.test {
                skipItems(1)

                repository.emit(listOf(jane, rahul))

                assertThat(awaitItem()).isEqualTo(
                    UsersState(
                        users =
                            listOf(
                                UserUi(id = 1, name = "Jane Doe", email = "jane.doe@example.com"),
                                UserUi(id = 2, name = "Rahul Sharma", email = "rahul.sharma@example.in"),
                            ),
                        isLoading = false,
                    ),
                )
            }
        }

    @Test
    fun emptyRepositoryEndsLoadingWithNoUsers() =
        runTest {
            viewModel.state.test {
                skipItems(1)

                repository.emit(emptyList())

                assertThat(awaitItem()).isEqualTo(UsersState(users = emptyList(), isLoading = false))
            }
        }

    private companion object {
        val jane =
            User(
                id = 1,
                name = "Jane Doe",
                email = "jane.doe@example.com",
                gender = Gender.FEMALE,
                status = UserStatus.ACTIVE,
                createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            )
        val rahul =
            User(
                id = 2,
                name = "Rahul Sharma",
                email = "rahul.sharma@example.in",
                gender = Gender.MALE,
                status = UserStatus.INACTIVE,
                createdAt = Instant.fromEpochMilliseconds(1_700_000_060_000),
            )
    }
}

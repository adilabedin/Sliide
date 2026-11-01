package com.androidlearning.sliide.users.presentation.delete

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.presentation.fake.FakeUserRepository
import com.androidlearning.sliide.users.presentation.fake.SchedulerClock
import com.androidlearning.sliide.users.presentation.fake.TEST_NOW
import com.androidlearning.sliide.users.presentation.fake.jane
import com.androidlearning.sliide.users.presentation.fake.rahul
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteUndoControllerTest {
    private val repository = FakeUserRepository()

    private fun TestScope.controller(): DeleteUndoController =
        DeleteUndoController(
            repository = repository,
            scope = backgroundScope,
            clock = SchedulerClock(testScheduler),
        )

    private suspend fun TestScope.seeded(): DeleteUndoController {
        repository.emit(listOf(jane, rahul))
        return controller()
    }

    @Test
    fun requestRemovesLocallyAndOpensWindow() =
        runTest {
            val controller = seeded()

            controller.request(jane.id)
            runCurrent()

            assertThat(repository.removedIds).containsExactly(jane.id)
            assertThat(controller.pending.value).isEqualTo(PendingDelete(jane, TEST_NOW + 5.seconds))
            assertThat(repository.finalizedUsers).isEmpty()
        }

    @Test
    fun undoRestoresAndNeverFinalises() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)
            advanceTimeBy(2.seconds)

            controller.undo(jane.id)
            advanceTimeBy(10.seconds)

            assertThat(repository.restoredUsers).containsExactly(jane)
            assertThat(repository.finalizedUsers).isEmpty()
            assertThat(controller.pending.value).isNull()
        }

    @Test
    fun expiryFinalisesExactlyOnce() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)

            advanceTimeBy(4_999)
            runCurrent()
            assertThat(repository.finalizedUsers).isEmpty()

            advanceTimeBy(2)
            runCurrent()
            assertThat(repository.finalizedUsers).containsExactly(jane)
            assertThat(controller.pending.value).isNull()

            advanceTimeBy(30.seconds)
            assertThat(repository.finalizedUsers).containsExactly(jane)
            assertThat(repository.restoredUsers).isEmpty()
        }

    @Test
    fun commitNowFinalisesImmediately() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)
            advanceTimeBy(1.seconds)

            controller.commitNow(jane.id)
            runCurrent()

            assertThat(repository.finalizedUsers).containsExactly(jane)
            advanceTimeBy(10.seconds)
            assertThat(repository.finalizedUsers).containsExactly(jane)
        }

    @Test
    fun secondDeleteFinalisesTheFirstAndStartsAFreshWindow() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)
            advanceTimeBy(2.seconds)

            controller.request(rahul.id)
            runCurrent()

            assertThat(repository.finalizedUsers).containsExactly(jane)
            assertThat(controller.pending.value).isEqualTo(PendingDelete(rahul, TEST_NOW + 7.seconds))

            advanceTimeBy(4.seconds)
            runCurrent()
            assertThat(repository.finalizedUsers).containsExactly(jane)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertThat(repository.finalizedUsers).containsExactly(jane, rahul)
            assertThat(repository.restoredUsers).isEmpty()
        }

    @Test
    fun staleUndoAndCommitForAnOlderUserAreIgnored() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)
            runCurrent()
            controller.request(rahul.id)
            runCurrent()

            controller.undo(jane.id)
            controller.commitNow(jane.id)
            runCurrent()

            assertThat(repository.restoredUsers).isEmpty()
            assertThat(repository.finalizedUsers).containsExactly(jane)
            assertThat(controller.pending.value?.user).isEqualTo(rahul)
        }

    @Test
    fun undoAfterExpiryDoesNothing() =
        runTest {
            val controller = seeded()
            controller.request(jane.id)
            advanceTimeBy(6.seconds)

            controller.undo(jane.id)
            runCurrent()

            assertThat(repository.restoredUsers).isEmpty()
            assertThat(repository.finalizedUsers).containsExactly(jane)
        }

    @Test
    fun requestForAMissingUserOpensNoWindow() =
        runTest {
            val controller = seeded()

            controller.request(999)
            advanceTimeBy(10.seconds)

            assertThat(controller.pending.value).isNull()
            assertThat(repository.finalizedUsers).isEmpty()
            assertThat(repository.restoredUsers).isEmpty()
        }

    @Test
    fun failedFinaliseEmitsAMessage() =
        runTest {
            val controller = seeded()
            repository.finalizeResult = Result.Failure(DataError.Remote.UNAUTHORIZED)

            controller.messages.test {
                controller.request(jane.id)
                advanceTimeBy(6.seconds)

                assertThat(awaitItem()).isEqualTo(
                    DeleteMessage.FinalizeFailed(userName = jane.name, error = DataError.Remote.UNAUTHORIZED),
                )
            }
        }

    @Test
    fun timerKeepsRunningWhenObserversGoAway() =
        runTest {
            val controller = seeded()
            val observerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            observerScope.launch { controller.pending.collect { } }

            controller.request(jane.id)
            runCurrent()
            observerScope.cancel()
            advanceTimeBy(6.seconds)

            assertThat(repository.finalizedUsers).containsExactly(jane)
        }
}

package com.androidlearning.sliide.users.data.sync

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.repository.RepositoryHarness
import com.androidlearning.sliide.users.data.repository.userEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** WorkManager scheduling and the worker's no-retry rule against a test WorkManager (design D3/D4). */
class WorkManagerUserSyncSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeTest
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun schedulesOnePeriodicWorkEvery30MinutesWhenConnected() {
        WorkManagerUserSyncScheduler(context).schedulePeriodicSync()

        val info = uniqueWork().single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun schedulingAgainKeepsTheExistingWork() {
        val scheduler = WorkManagerUserSyncScheduler(context)
        scheduler.schedulePeriodicSync()
        val firstId = uniqueWork().single().id

        scheduler.schedulePeriodicSync()

        assertThat(uniqueWork()).single().transform { it.id }.isEqualTo(firstId)
    }

    @Test
    fun workerReportsSuccessEvenWhenTheReplayStops() =
        runTest {
            val harness = RepositoryHarness(scope)
            harness.seedPending(harness.seedUser(userEntity(1)), PendingOperation.UPDATE)
            harness.apiClient.updateResults += Result.Failure(DataError.Remote.SERVER_ERROR)
            val worker =
                TestListenableWorkerBuilder<UserSyncWorker>(context)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker = UserSyncWorker(appContext, workerParameters, BackgroundUserSync(harness.repository))
                        },
                    ).build()

            val result = worker.doWork()

            assertThat(result).isEqualTo(ListenableWorker.Result.success())
            // The replay really stopped: the entry is still queued for the next periodic run.
            assertThat(harness.pendingSyncDao.entries).single().transform { it.user.id }.isEqualTo(1L)
        }

    @Test
    fun requestSyncEnqueuesOneTimeWorkForThatUserWhenConnected() {
        WorkManagerUserSyncScheduler(context).requestSync(42)

        val info = userWork(42).single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.periodicityInfo).isNull()
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(userWork(41)).isEmpty()
    }

    @Test
    fun requestingTwiceForOneUserLeavesOnePendingJob() {
        val scheduler = WorkManagerUserSyncScheduler(context)
        scheduler.requestSync(42)
        val firstId = userWork(42).single().id

        scheduler.requestSync(42)

        val pending = userWork(42).filterNot { it.state.isFinished }
        assertThat(pending).single().transform { it.id }.isNotEqualTo(firstId)
    }

    @Test
    fun requestSyncLeavesThePeriodicWorkUntouched() {
        val scheduler = WorkManagerUserSyncScheduler(context)
        scheduler.schedulePeriodicSync()
        val periodicId = uniqueWork().single().id

        scheduler.requestSync(42)

        assertThat(uniqueWork()).single().transform { it.id }.isEqualTo(periodicId)
    }

    @Test
    fun workerWithAUserIdSendsOnlyThatUsersEntry() =
        runTest {
            val harness = RepositoryHarness(scope)
            harness.seedPending(harness.seedUser(userEntity(41)), PendingOperation.UPDATE, queuedAt = 100)
            harness.seedPending(harness.seedUser(userEntity(42)), PendingOperation.UPDATE, queuedAt = 200)
            val worker =
                TestListenableWorkerBuilder<UserSyncWorker>(context)
                    .setInputData(workDataOf(UserSyncWorker.KEY_USER_ID to 42L))
                    .setWorkerFactory(workerFactory(harness))
                    .build()

            val result = worker.doWork()

            assertThat(result).isEqualTo(ListenableWorker.Result.success())
            assertThat(harness.pendingSyncDao.entries.map { it.user.id }).containsExactly(41L)
        }

    private fun workerFactory(harness: RepositoryHarness): WorkerFactory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = UserSyncWorker(appContext, workerParameters, BackgroundUserSync(harness.repository))
        }

    private fun userWork(userId: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WorkManagerUserSyncScheduler.userWorkName(userId)).get()

    private fun uniqueWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerUserSyncScheduler.UNIQUE_WORK_NAME).get()
}

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
import assertk.assertThat
import assertk.assertions.isEqualTo
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

    private fun uniqueWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerUserSyncScheduler.UNIQUE_WORK_NAME).get()
}

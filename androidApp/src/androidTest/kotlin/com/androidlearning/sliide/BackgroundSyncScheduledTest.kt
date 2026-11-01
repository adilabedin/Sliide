package com.androidlearning.sliide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The real app process: `SliideApplication` initialised WorkManager through Koin's factory (the default initializer is
 * removed in the manifest) and scheduled exactly one periodic background sync (spec: background-sync).
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncScheduledTest {
    @Test
    fun periodicSyncIsScheduledOnceOnLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Same name as WorkManagerUserSyncScheduler.UNIQUE_WORK_NAME, which :androidApp can't see (it's in :users:data).
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("sync_pending_users").get()

        assertEquals(1, infos.size)
        val info = infos.single()
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
        assertEquals(TimeUnit.MINUTES.toMillis(30), info.periodicityInfo?.repeatIntervalMillis)
    }
}

package com.androidlearning.sliide.core.database.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.androidlearning.sliide.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide.core.database.RoomDatabaseTransactionRunner
import com.androidlearning.sliide.core.database.SliideDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Provides a `RoomDatabase.Builder<SliideDatabase>`. Platform-specific because Android needs a
 * `Context` and iOS needs the documents directory path.
 */
expect val platformDatabaseModule: Module

/** Builds the database identically on every platform from the platform builder. */
val databaseModule =
    module {
        single<SliideDatabase> {
            get<RoomDatabase.Builder<SliideDatabase>>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single { get<SliideDatabase>().userDao }
        single { get<SliideDatabase>().pendingSyncDao }
        single<DatabaseTransactionRunner> { RoomDatabaseTransactionRunner(get()) }
    }

package com.androidlearning.sliide.core.database.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.androidlearning.sliide.core.database.DatabaseConstants
import com.androidlearning.sliide.core.database.SliideDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val platformDatabaseModule: Module =
    module {
        single<RoomDatabase.Builder<SliideDatabase>> {
            Room.databaseBuilder<SliideDatabase>(name = "${documentDirectory()}/${DatabaseConstants.FILE_NAME}")
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(directory?.path) { "iOS documents directory is unavailable" }
}

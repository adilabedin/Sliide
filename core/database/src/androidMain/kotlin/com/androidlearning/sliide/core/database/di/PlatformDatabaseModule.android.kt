package com.androidlearning.sliide.core.database.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.androidlearning.sliide.core.database.DatabaseConstants
import com.androidlearning.sliide.core.database.SliideDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module =
    module {
        single<RoomDatabase.Builder<SliideDatabase>> {
            val context = androidContext().applicationContext
            Room.databaseBuilder<SliideDatabase>(
                context = context,
                name = context.getDatabasePath(DatabaseConstants.FILE_NAME).absolutePath,
            )
        }
    }

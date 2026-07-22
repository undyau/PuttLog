package com.undy.puttlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Putt::class], version = 1, exportSchema = false)
abstract class PuttDatabase : RoomDatabase() {
    abstract fun puttDao(): PuttDao

    companion object {
        @Volatile
        private var instance: PuttDatabase? = null

        fun getInstance(context: Context): PuttDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PuttDatabase::class.java,
                    "putt-log.db"
                ).build().also { instance = it }
            }
    }
}

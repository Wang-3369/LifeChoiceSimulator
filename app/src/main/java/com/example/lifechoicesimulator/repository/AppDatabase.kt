package com.example.lifechoicesimulator.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.lifechoicesimulator.model.Event
import com.example.lifechoicesimulator.model.EventConverters

@Database(entities = [Event::class], version = 1, exportSchema = false)
@TypeConverters(EventConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life_choice_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
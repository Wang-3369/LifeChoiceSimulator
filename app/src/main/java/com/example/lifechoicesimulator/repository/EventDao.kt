package com.example.lifechoicesimulator.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifechoicesimulator.model.Event

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<Event>)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents(): Int

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): Event?

    // 透過 SQLite 高效篩選：年齡符合，且 worldview JSON 字串中包含該世界觀或 common
    @Query("""
        SELECT * FROM events 
        WHERE minAge <= :age AND maxAge >= :age 
        AND (worldview LIKE '%' || :worldview || '%' OR worldview LIKE '%common%')
    """)
    suspend fun getEventsByAgeAndWorldview(age: Int, worldview: String): List<Event>
}

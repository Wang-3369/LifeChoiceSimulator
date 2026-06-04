package com.example.lifechoicesimulator.repository

import android.content.Context
import com.example.lifechoicesimulator.model.Event
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class EventRepository(private val context: Context) {

    private val eventDao = AppDatabase.getDatabase(context).eventDao()

    suspend fun initializeDatabaseIfNeeded() {
        try {
            withContext(Dispatchers.IO) {
                // 👉 開發階段專用：每次啟動都清空舊資料，重新讀取 JSON
                eventDao.deleteAllEvents()

                val allEvents = loadAllEventsFromAssets()
                if (allEvents.isNotEmpty()) {
                    eventDao.insertAll(allEvents)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAllEventsFromAssets(): List<Event> {
        val allEvents = mutableListOf<Event>()
        val gson = Gson()
        val listType = object : TypeToken<List<Event>>() {}.type
        val assetManager = context.assets

        try {
            // 直接掃描 events/ 目錄下的所有檔案
            val files = assetManager.list("events") ?: emptyArray()

            for (fileName in files) {
                // 只讀取 .json 結尾的檔案 (例如 urban.json, common.json)
                if (fileName.endsWith(".json")) {
                    val inputStream = assetManager.open("events/$fileName")
                    val reader = InputStreamReader(inputStream)

                    val eventsInFile: List<Event> = gson.fromJson(reader, listType) ?: emptyList()
                    allEvents.addAll(eventsInFile)

                    reader.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return allEvents
    }

    suspend fun getEventById(eventId: String): Event? {
        return eventDao.getEventById(eventId)
    }

    suspend fun getAvailableEvents(
        currentAge: Int,
        currentStats: Map<String, Int>,
        currentFlags: Set<String>,
        playerWorldview: String
    ): List<Pair<Event, Double>> {

        val initialEvents = eventDao.getEventsByAgeAndWorldview(currentAge, playerWorldview)

        return initialEvents.mapNotNull { event ->
            // 安全判定：因為 Event 欄位現在允許 null，我們需要用 ?. 和 ?: true 處理
            val statsMatch = event.requiredStats?.all { (statName, requiredValue) ->
                val playerStatValue = currentStats[statName] ?: 0
                playerStatValue >= requiredValue
            } ?: true

            val flagsMatch = event.requiredFlags?.all { requiredFlag ->
                currentFlags.contains(requiredFlag)
            } ?: true

            val forbiddenMatch = event.forbiddenFlags?.none { currentFlags.contains(it) } ?: true

            if (statsMatch && flagsMatch) {
                var finalWeight = event.probability
                event.weightModifiers?.forEach { (flag, modifier) ->
                    if (currentFlags.contains(flag)) {
                        finalWeight *= modifier
                    }
                }
                Pair(event, finalWeight)
            } else {
                null
            }
        }
    }
}
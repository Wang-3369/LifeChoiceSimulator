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
        playerWorldview: String,
        actionTag: String? = null,
        excludedEventIds: Set<String> = emptySet()
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
            val eventActionTags = event.actionTags.orEmpty().ifEmpty { inferActionTags(event.id) }
            val actionMatch = actionTag == null || eventActionTags.isEmpty() || eventActionTags.contains(actionTag)

            val notRecentlyTriggered = !excludedEventIds.contains(event.id)

            if (statsMatch && flagsMatch && forbiddenMatch && actionMatch && notRecentlyTriggered) {
                var finalWeight = event.probability
                event.weightModifiers?.forEach { (flag, modifier) ->
                    if (currentFlags.contains(flag)) {
                        finalWeight *= modifier
                    }
                }
                val luck = currentStats["luck"] ?: 0
                val isDangerousHealthEvent = event.eventType == "death" || event.choices.orEmpty().any { choice ->
                    (choice.statChanges?.get("health") ?: 0) <= -40 ||
                        (choice.failStatChanges?.get("health") ?: 0) <= -40
                }
                val luckFactor = when {
                    isDangerousHealthEvent && luck < 0 -> (1.0 + kotlin.math.abs(luck) * 0.03).coerceIn(1.0, 8.0)
                    isDangerousHealthEvent && luck >= 0 -> (1.0 - luck * 0.006).coerceIn(0.15, 1.0)
                    event.eventType == "chain" || event.eventType == "mini_game" -> (1.0 + luck * 0.002).coerceIn(0.5, 1.8)
                    else -> 1.0
                }
                finalWeight *= luckFactor

                if (finalWeight > 0.0) Pair(event, finalWeight) else null
            } else {
                null
            }
        }
    }

    private fun inferActionTags(eventId: String): List<String> {
        return when {
            eventId.contains("library") || eventId.contains("exam") || eventId.contains("alchemy") ||
                eventId.contains("secret_realm_inner") || eventId.contains("nascent") ||
                eventId.contains("spirit_root") -> listOf("study")
            eventId.contains("bus") || eventId.contains("sword_array") || eventId.contains("tribulation") ||
                eventId.contains("sect_war") || eventId.contains("accident") -> listOf("exercise")
            eventId.contains("stock") || eventId.contains("startup") || eventId.contains("career") ||
                eventId.contains("demonic") -> listOf("online")
            eventId.contains("travel") || eventId.contains("crosswalk") || eventId.contains("secret_realm_entrance") ||
                eventId.contains("master") || eventId.contains("dao_companion") || eventId.contains("ascension") -> listOf("travel")
            eventId.contains("hospital") || eventId.contains("family") || eventId.contains("retirement") ||
                eventId.contains("apprentice") || eventId.contains("community") -> listOf("rest")
            else -> emptyList()
        }
    }
}

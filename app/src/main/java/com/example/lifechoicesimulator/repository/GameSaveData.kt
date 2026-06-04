package com.example.lifechoicesimulator.repository
import com.example.lifechoicesimulator.model.Event
// GameSaveData.kt (新檔案)
data class GameSaveData(
    val character: Character,
    val currentEvent: Event?,
    val eventResultText: String,
    val forcedNextEventId: String?,
    val saveTime: Long = System.currentTimeMillis()
)
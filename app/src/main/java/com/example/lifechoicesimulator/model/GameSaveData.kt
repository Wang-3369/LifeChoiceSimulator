package com.example.lifechoicesimulator.model

// 用來打包所有需要存檔的狀態
data class GameSaveData(
    val character: Character,
    val currentEvent: Event?,
    val eventResultText: String,
    val forcedNextEventId: String?,
    val recentEventIds: List<String> = emptyList(),
    val lifeLog: List<String> = emptyList(),
    val finalLifeStory: String = "",
    val saveTime: Long = System.currentTimeMillis()
)

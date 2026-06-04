package com.example.lifechoicesimulator.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val minAge: Int,
    val maxAge: Int,
    val probability: Double,

    // 加上 ? 允許 Gson 在找不到欄位時填入 null，避免 Room 寫入時崩潰
    val requiredStats: Map<String, Int>? = emptyMap(),
    val choices: List<Choice>? = emptyList(),
    val requiredFlags: List<String>? = emptyList(),
    val forbiddenFlags: List<String>? = emptyList(),
    val weightModifiers: Map<String, Double>? = emptyMap(),
    val worldview: List<String>? = listOf("common"),
    val eventType: String? = "daily"
)

data class Choice(
    val choiceText: String,
    val condition: String,
    val successResultText: String,
    val failResultText: String,

    // 成功時的變化
    val statChanges: Map<String, Int>? = emptyMap(),
    val addFlag: String? = null,

    // 👉 新增：失敗時的變化
    val failStatChanges: Map<String, Int>? = emptyMap(),
    val failAddFlag: String? = null,

    val nextEventId: String? = null
)
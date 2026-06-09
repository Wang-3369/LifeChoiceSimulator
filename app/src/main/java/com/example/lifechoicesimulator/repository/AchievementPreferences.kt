package com.example.lifechoicesimulator.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson // 👉 新增：匯入 Gson
import com.example.lifechoicesimulator.model.GameSaveData// 👉 注意：請依據你存放 GameSaveData 的 package 位置將其 import 進來

import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.map

// 建立 DataStore 擴充屬性 (確保在頂層宣告，避免重複實例化)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "achievements_settings")

class AchievementPreferences(private val context: Context) {
    private val gson = Gson()

    // 👉 修正：將所有靜態常數合併到同一個 companion object 中
    companion object {
        // 定義三個存檔 Slot 的 Key
        val SLOT_1 = stringPreferencesKey("save_slot_1")
        val SLOT_2 = stringPreferencesKey("save_slot_2")
        val SLOT_3 = stringPreferencesKey("save_slot_3")

        // 用於紀錄玩家當前正在哪一個存檔槽遊玩 (0 代表新遊戲，1~3 代表對應槽)
        val CURRENT_PLAYING_SLOT = stringPreferencesKey("current_playing_slot")

        // 使用 StringSet 來儲存解鎖的成就名稱或 ID
        val UNLOCKED_ACHIEVEMENTS = stringSetPreferencesKey("unlocked_achievements")
        val BGM_ENABLED = booleanPreferencesKey("bgm_enabled")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val BGM_VOLUME = floatPreferencesKey("bgm_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val API_ENABLED = booleanPreferencesKey("api_enabled")
        val API_KEY = stringPreferencesKey("api_key")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val API_MODEL = stringPreferencesKey("api_model")
        val ONLINE_ENABLED = booleanPreferencesKey("online_enabled")
        val ONLINE_BASE_URL = stringPreferencesKey("online_base_url")
        val ONLINE_PLAYER_NAME = stringPreferencesKey("online_player_name")
    }



    val bgmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[BGM_ENABLED] ?: true }
    val sfxEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SFX_ENABLED] ?: true }
    val vibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[VIBRATION_ENABLED] ?: true }
    val apiEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[API_ENABLED] ?: false }
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val apiEndpointFlow: Flow<String> = context.dataStore.data.map { it[API_ENDPOINT] ?: "https://api.openai.com/v1/chat/completions" }
    val apiModelFlow: Flow<String> = context.dataStore.data.map { it[API_MODEL] ?: "gpt-4.1-mini" }
    val onlineEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[ONLINE_ENABLED] ?: false }
    val onlineBaseUrlFlow: Flow<String> = context.dataStore.data.map { it[ONLINE_BASE_URL] ?: "https://lifechoicebackonline.onrender.com" }
    val onlinePlayerNameFlow: Flow<String> = context.dataStore.data.map { it[ONLINE_PLAYER_NAME] ?: "匿名玩家" }
    suspend fun <T> setSetting(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    val bgmVolumeFlow: Flow<Float> = context.dataStore.data.map { it[BGM_VOLUME] ?: 0.5f }
    val sfxVolumeFlow: Flow<Float> = context.dataStore.data.map { it[SFX_VOLUME] ?: 0.5f }
    private fun getSlotKey(slotId: Int): Preferences.Key<String> {
        return when (slotId) {
            1 -> SLOT_1
            2 -> SLOT_2
            3 -> SLOT_3
            else -> throw IllegalArgumentException("Invalid slot ID")
        }
    }



    // 取得特定存檔內容 (Flow)
    fun getSaveSlotFlow(slotId: Int): Flow<GameSaveData?> = context.dataStore.data.map { prefs ->
        val json = prefs[getSlotKey(slotId)]
        if (!json.isNullOrEmpty()) {
            gson.fromJson(json, GameSaveData::class.java)
        } else {
            null
        }
    }

    // 儲存遊戲
    suspend fun saveGame(slotId: Int, saveData: GameSaveData) {
        context.dataStore.edit { prefs ->
            val json = gson.toJson(saveData)
            prefs[getSlotKey(slotId)] = json
        }
    }

    // 取得目前已解鎖的成就列表，回傳 Flow 以供 ViewModel 監聽
    val unlockedAchievementsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[UNLOCKED_ACHIEVEMENTS] ?: emptySet()
    }

    // 新增成就
    suspend fun unlockAchievement(achievementName: String) {
        context.dataStore.edit { preferences ->
            val currentAchievements = preferences[UNLOCKED_ACHIEVEMENTS] ?: emptySet()
            if (!currentAchievements.contains(achievementName)) {
                preferences[UNLOCKED_ACHIEVEMENTS] = currentAchievements + achievementName
            }
        }
    }
}

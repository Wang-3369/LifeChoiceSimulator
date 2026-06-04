package com.example.lifechoicesimulator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifechoicesimulator.model.Character
import com.example.lifechoicesimulator.model.Choice
import com.example.lifechoicesimulator.model.Event
import com.example.lifechoicesimulator.model.GameSaveData
import com.example.lifechoicesimulator.repository.AchievementPreferences
import com.example.lifechoicesimulator.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EventRepository(application)
    private val achievementPrefs = AchievementPreferences(application)
    private val soundManager = com.example.lifechoicesimulator.util.SoundManager(application)
    val bgmEnabled =
        achievementPrefs.bgmEnabledFlow.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            true
        )

    val sfxEnabled =
        achievementPrefs.sfxEnabledFlow.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            true
        )

    val vibrationEnabled =
        achievementPrefs.vibrationEnabledFlow.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            true
        )
    private val _characterState = MutableStateFlow(Character())
    val characterState: StateFlow<Character> = _characterState.asStateFlow()

    private val _currentEvent = MutableStateFlow<Event?>(null)
    val currentEvent: StateFlow<Event?> = _currentEvent.asStateFlow()

    private val _eventResultText = MutableStateFlow("")
    val eventResultText: StateFlow<String> = _eventResultText.asStateFlow()

    private val _isGameOver = MutableStateFlow(false)
    val isGameOver: StateFlow<Boolean> = _isGameOver.asStateFlow()

    private val _isAiModeEnabled = MutableStateFlow(false)
    val isAiModeEnabled: StateFlow<Boolean> = _isAiModeEnabled.asStateFlow()

    private var forcedNextEventId: String? = null

    private val _unlockedAchievements = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievements: StateFlow<Set<String>> = _unlockedAchievements.asStateFlow()

    // 自由分配點數狀態
    private val _allocatablePoints = MutableStateFlow(0)
    val allocatablePoints: StateFlow<Int> = _allocatablePoints.asStateFlow()

    private val _allocatedStats = MutableStateFlow(
        mapOf(
            "health" to 0, "intelligence" to 0, "charisma" to 0,
            "luck" to 0, "morality" to 0, "wealth" to 0
        )
    )
    val allocatedStats: StateFlow<Map<String, Int>> = _allocatedStats.asStateFlow()

    // ===== 新增：世界觀狀態 =====
    private val _selectedWorldview = MutableStateFlow("urban")
    val selectedWorldview: StateFlow<String> = _selectedWorldview.asStateFlow()
    val bgmVolume = achievementPrefs.bgmVolumeFlow.stateIn(viewModelScope, SharingStarted.Lazily, 0.5f)
    val sfxVolume = achievementPrefs.sfxVolumeFlow.stateIn(viewModelScope, SharingStarted.Lazily, 0.5f)
    // ===== 新增：存檔槽追蹤 =====
    private var currentSlotId: Int = 0

    init {
        viewModelScope.launch {
            achievementPrefs.unlockedAchievementsFlow.collect { achievements ->
                _unlockedAchievements.value = achievements
            }
        }
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
        }
        viewModelScope.launch {
            bgmVolume.collect { soundManager.bgmVolume = it }
        }
        viewModelScope.launch {
            sfxVolume.collect { soundManager.sfxVolume = it }
        }
    }
    fun playMenuBgm() {
        soundManager.playBgm("main_menu")
    }
    fun setBgmVolume(volume: Float) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.BGM_VOLUME, volume) }
    fun setSfxVolume(volume: Float) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.SFX_VOLUME, volume) }

    fun toggleBgm(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.BGM_ENABLED, enabled) }
    fun toggleSfx(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.SFX_ENABLED, enabled) }
    fun toggleVibration(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.VIBRATION_ENABLED, enabled) }

    // ===== 存檔與讀檔邏輯 =====
    fun getSaveSlotData(slotId: Int): Flow<GameSaveData?> {
        return achievementPrefs.getSaveSlotFlow(slotId)
    }

    fun saveCurrentGame(slotId: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val saveData = GameSaveData(
                character = _characterState.value,
                currentEvent = _currentEvent.value,
                eventResultText = _eventResultText.value,
                forcedNextEventId = forcedNextEventId
            )
            achievementPrefs.saveGame(slotId, saveData)
            currentSlotId = slotId
            onComplete()
        }
    }

    fun loadGameFromSlot(slotId: Int, saveData: GameSaveData) {
        currentSlotId = slotId
        _characterState.value = saveData.character
        _currentEvent.value = saveData.currentEvent
        _eventResultText.value = saveData.eventResultText
        forcedNextEventId = saveData.forcedNextEventId
        _isGameOver.value = false
    }

    // ===== 遊戲初始化與屬性分配邏輯 =====
    fun selectWorldview(worldview: String) {
        _selectedWorldview.value = worldview
    }

    fun prepareNewGame() {
        _allocatablePoints.value = _unlockedAchievements.value.size * 5
        _allocatedStats.value = mapOf(
            "health" to 0, "intelligence" to 0, "charisma" to 0,
            "luck" to 0, "morality" to 0, "wealth" to 0
        )
        currentSlotId = 0 // 重置為新遊戲
    }

    fun allocatePoint(statName: String, isAdding: Boolean) {
        val currentAllocated = _allocatedStats.value.toMutableMap()
        val currentPoints = _allocatablePoints.value

        if (isAdding && currentPoints > 0) {
            currentAllocated[statName] = (currentAllocated[statName] ?: 0) + 1
            _allocatablePoints.value -= 1
        } else if (!isAdding && (currentAllocated[statName] ?: 0) > 0) {
            currentAllocated[statName] = (currentAllocated[statName] ?: 0) - 1
            _allocatablePoints.value += 1
        }
        _allocatedStats.value = currentAllocated
    }

    fun confirmAndStartGame() {
        val allocated = _allocatedStats.value

        val randomInt = (-100..100).random() + (allocated["intelligence"] ?: 0)
        val randomCha = (-100..100).random() + (allocated["charisma"] ?: 0)
        val randomLuck = (-100..100).random() + (allocated["luck"] ?: 0)
        val randomMorality = (-100..100).random() + (allocated["morality"] ?: 0)

        val backgrounds = listOf("貧窮", "普通", "富裕")
        val startBackground = backgrounds.random()
        val startWealth = when (startBackground) {
            "貧窮" -> -50
            "富裕" -> 50
            else -> 0
        } + (allocated["wealth"] ?: 0)

        _characterState.value = Character(
            age = 0,
            health = 100 + (allocated["health"] ?: 0),
            intelligence = randomInt,
            charisma = randomCha,
            luck = randomLuck,
            morality = randomMorality,
            wealth = startWealth,
            familyBackground = startBackground,
            worldview = _selectedWorldview.value, // 寫入玩家選擇的世界觀
            flags = mutableSetOf()
        )
        soundManager.playBgm(_selectedWorldview.value)
        _isGameOver.value = false
        forcedNextEventId = null

        _eventResultText.value = generateBirthReport(_characterState.value)
        advanceTurn()
    }

    private fun generateBirthReport(char: Character): String {
        val wealthDesc = when (char.familyBackground) {
            "富裕" -> "你含著金湯匙出生在富裕家庭。"
            "貧窮" -> "你出生在一個家徒四壁的貧困家庭。"
            else -> "你出生在一個普通平凡的家庭。"
        }
        val intDesc =
            if (char.intelligence > 50) "從小就展現出過人的智慧。" else if (char.intelligence < -50) "看起來似乎比別的小孩遲鈍些。" else ""
        return "【出生報告】\n$wealthDesc $intDesc".trim()
    }

    private fun advanceTurn() {
        val currentChar = _characterState.value

        if (currentChar.health <= 0 || currentChar.age >= 100) {
            _isGameOver.value = true
            checkAndUnlockAchievements(currentChar)
            return
        }

        _characterState.value = currentChar.copy(age = currentChar.age + 1)
        triggerRandomEvent()
    }

    fun toggleAiMode() {
        _isAiModeEnabled.value = !_isAiModeEnabled.value
        if (_isAiModeEnabled.value) {
            triggerAiEvent()
        } else {
            triggerRandomEvent()
        }
    }

    private fun triggerAiEvent() {
        viewModelScope.launch {
            if (forcedNextEventId != null) {
                val forcedEvent = repository.getEventById(forcedNextEventId!!)
                if (forcedEvent != null) {
                    _currentEvent.value = forcedEvent
                    forcedNextEventId = null
                    return@launch
                }
            }

            _currentEvent.value = Event(
                id = "ai_event_01",
                title = "[AI 生成] 神秘的機遇",
                description = "(由 API 即時生成) 你在路上撿到了一個發光的奇怪硬幣，要帶回家嗎？",
                minAge = 0, maxAge = 100, probability = 1.0,
                choices = listOf(
                    Choice("帶回家研究", "", "硬幣突然化作粉末，你覺得自己變聰明了。", "什麼都沒發生。", mapOf("intelligence" to 10)),
                    Choice("丟在原地", "", "你安分守己地離開了。", "你覺得有點可惜。", mapOf("morality" to 5))
                )
            )
        }
    }

    private fun triggerRandomEvent() {
        val currentChar = _characterState.value

        viewModelScope.launch {
            if (forcedNextEventId != null) {
                val forcedEvent = repository.getEventById(forcedNextEventId!!)
                if (forcedEvent != null) {
                    _currentEvent.value = forcedEvent
                    forcedNextEventId = null
                    return@launch
                }
            }

            val statsMap = mapOf(
                "intelligence" to currentChar.intelligence,
                "charisma" to currentChar.charisma,
                "health" to currentChar.health,
                "luck" to currentChar.luck,
                "morality" to currentChar.morality,
                "wealth" to currentChar.wealth
            )

            val availableEventsWithWeights = repository.getAvailableEvents(
                currentAge = currentChar.age,
                currentStats = statsMap,
                currentFlags = currentChar.flags,
                playerWorldview = currentChar.worldview
            )

            if (availableEventsWithWeights.isNotEmpty()) {
                val totalWeight = availableEventsWithWeights.sumOf { it.second }
                var randomValue = Math.random() * totalWeight

                for (pair in availableEventsWithWeights) {
                    randomValue -= pair.second
                    if (randomValue <= 0) {
                        _currentEvent.value = pair.first
                        break
                    }
                }
            } else {
                _currentEvent.value = Event(
                    id = "empty_year",
                    title = "平淡的一年",
                    description = "今年平安無事地過去了，沒有特別的事情發生。",
                    minAge = 0, maxAge = 100, probability = 1.0, choices = listOf(Choice("繼續生活", "", "時間繼續流逝...", ""))
                )
            }
        }
    }

    // ===== 更新：確保玩家的選擇會真的改變屬性 =====
    // ===== 更新：根據成功/失敗套用不同的屬性變化 =====
    fun makeChoice(choiceIndex: Int) {
        val event = _currentEvent.value ?: return
        val choices = event.choices ?: return
        if (choiceIndex >= choices.size) return

        val choice = choices[choiceIndex]
        var updatedChar = _characterState.value
        soundManager.playClickSound()
        soundManager.vibrate()

        // 1. 先判定成功或失敗！
        // 簡易判定：如果有設定 failResultText，就有 70% 成功率；否則 100% 成功。
        val isSuccess = if (choice.failResultText.isNotEmpty()) {
            val currentLuck = updatedChar.luck
            val luckBonus = currentLuck * 0.001
            val finalSuccessRate = (0.70 + luckBonus).coerceIn(0.0, 1.0)
            Math.random() <= finalSuccessRate
        } else {
            true
        }

        // 2. 根據判定結果，抓取對應的文本、屬性變化與 Flag
        val actionResultText = if (isSuccess) choice.successResultText else choice.failResultText
        val currentStatChanges = if (isSuccess) choice.statChanges else choice.failStatChanges
        val currentAddFlag = if (isSuccess) choice.addFlag else choice.failAddFlag

        // 3. 處理屬性變化並記錄 Log
        val statNames = mapOf(
            "health" to "體力", "intelligence" to "智力", "charisma" to "魅力",
            "luck" to "運氣", "morality" to "道德", "wealth" to "財富"
        )

        val statLog = mutableListOf<String>()

        currentStatChanges?.forEach { (stat, change) ->
            updatedChar = when (stat) {
                "health" -> updatedChar.copy(health = (updatedChar.health + change).coerceIn(0, 100))
                "intelligence" -> updatedChar.copy(intelligence = updatedChar.intelligence + change)
                "charisma" -> updatedChar.copy(charisma = updatedChar.charisma + change)
                "luck" -> updatedChar.copy(luck = updatedChar.luck + change)
                "morality" -> updatedChar.copy(morality = updatedChar.morality + change)
                "wealth" -> updatedChar.copy(wealth = updatedChar.wealth + change)
                else -> updatedChar
            }
            val statName = statNames[stat] ?: stat
            val sign = if (change >= 0) "+" else ""
            statLog.add("$statName $sign$change")
        }

        // 4. 處理 Flag (如果是失敗，就塞入失敗的 Flag)
        currentAddFlag?.let { flag ->
            val updatedFlags = updatedChar.flags.toMutableSet().apply { add(flag) }
            updatedChar = updatedChar.copy(flags = updatedFlags)
        }

        _characterState.value = updatedChar

        if (!choice.nextEventId.isNullOrEmpty()) {
            forcedNextEventId = choice.nextEventId
        }

        // 5. 組合最終字串並顯示
        val logString = if (statLog.isNotEmpty()) "\n(變化：${statLog.joinToString(", ")})" else ""
        _eventResultText.value = "【抉擇結果】\n$actionResultText$logString"

        advanceTurn()
    }

    private fun checkAndUnlockAchievements(char: Character) {
        viewModelScope.launch {
            // 原有成就
            if (char.wealth > 1000) achievementPrefs.unlockAchievement("商業奇才")
            if (char.intelligence > 100) achievementPrefs.unlockAchievement("學術泰斗")
            if (char.morality < -100) achievementPrefs.unlockAchievement("法外狂徒")
            if (char.age >= 100) achievementPrefs.unlockAchievement("世紀人瑞")
            if (char.flags.contains("拯救世界")) achievementPrefs.unlockAchievement("救世主")

            // 新增的隨機成就
            if (char.wealth < -50) achievementPrefs.unlockAchievement("窮神附體")
            if (char.luck > 90) achievementPrefs.unlockAchievement("天選之人")
            if (char.charisma > 90) achievementPrefs.unlockAchievement("萬人迷")
            if (char.health <= 0 && char.age <= 18) achievementPrefs.unlockAchievement("英年早逝")

            // 如果活到老且屬性都很平庸 (無一超過 50，也沒低於 -50)
            if (char.age >= 60 &&
                char.wealth in -50..50 && char.intelligence in -50..50 &&
                char.morality in -50..50 && char.luck in -50..50) {
                achievementPrefs.unlockAchievement("平凡是福")
            }
        }
    }
}
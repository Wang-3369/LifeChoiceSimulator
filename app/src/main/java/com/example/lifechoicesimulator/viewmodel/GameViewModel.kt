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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.Gson

data class GameAction(
    val key: String,
    val label: String,
    val description: String,
    val statChanges: Map<String, Int>
)

data class DiceRollResult(
    val roll: Int,
    val target: Int,
    val luckModifier: Int,
    val success: Boolean
)

data class MiniGameState(
    val eventId: String,
    val target: Int,
    val attemptsRemaining: Int,
    val message: String
)

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
    val apiEnabled = achievementPrefs.apiEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val apiKey = achievementPrefs.apiKeyFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val apiEndpoint = achievementPrefs.apiEndpointFlow.stateIn(viewModelScope, SharingStarted.Lazily, "https://api.openai.com/v1/chat/completions")
    val apiModel = achievementPrefs.apiModelFlow.stateIn(viewModelScope, SharingStarted.Lazily, "gpt-4.1-mini")
    private val gson = Gson()
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
    private val recentEventIds = ArrayDeque<String>()
    private val recentEventCooldownSize = 6
    private val _lifeLog = MutableStateFlow<List<String>>(emptyList())
    val lifeLog: StateFlow<List<String>> = _lifeLog.asStateFlow()
    private val _finalLifeStory = MutableStateFlow("")
    val finalLifeStory: StateFlow<String> = _finalLifeStory.asStateFlow()
    private val _lastDiceRoll = MutableStateFlow<DiceRollResult?>(null)
    val lastDiceRoll: StateFlow<DiceRollResult?> = _lastDiceRoll.asStateFlow()
    private val _miniGameState = MutableStateFlow<MiniGameState?>(null)
    val miniGameState: StateFlow<MiniGameState?> = _miniGameState.asStateFlow()

    fun getAvailableActions(): List<GameAction> {
        return when (_characterState.value.worldview) {
            "cultivation" -> listOf(
                GameAction("rest", "閉關調息", "恢復氣血，較容易觸發心境與宗門事件。", mapOf("health" to 10, "luck" to 1)),
                GameAction("study", "研讀功法", "提升悟性，較容易觸發丹道、心法、解謎事件。", mapOf("intelligence" to 7, "health" to -2)),
                GameAction("exercise", "演練劍訣", "淬鍊體魄，較容易觸發戰鬥與渡劫事件。", mapOf("health" to 8, "charisma" to 1)),
                GameAction("online", "打探坊市", "蒐集消息與機緣，較容易觸發交易、魔門與傳聞事件。", mapOf("intelligence" to 3, "luck" to 3, "wealth" to -3)),
                GameAction("travel", "下山歷練", "踏入山川秘境，較容易觸發奇遇與連續事件。", mapOf("wealth" to -10, "luck" to 6, "charisma" to 2))
            )
            "superpower" -> listOf(
                GameAction("rest", "穩定精神", "恢復體力，降低能力失控風險。", mapOf("health" to 10, "luck" to 1)),
                GameAction("study", "能力研究", "分析能力規則，較容易觸發實驗事件。", mapOf("intelligence" to 7, "health" to -2)),
                GameAction("exercise", "能力訓練", "提升控制力，較容易觸發戰鬥與救援事件。", mapOf("health" to 7, "charisma" to 2)),
                GameAction("online", "追蹤異常論壇", "取得情報，較容易觸發都市傳聞。", mapOf("intelligence" to 3, "luck" to 3, "health" to -2)),
                GameAction("travel", "巡查街區", "外出尋找異常，較容易觸發現場事件。", mapOf("wealth" to -8, "luck" to 5, "morality" to 2))
            )
            else -> listOf(
                GameAction("rest", "休息", "恢復體力，較容易觸發生活與健康事件。", mapOf("health" to 10, "wealth" to -2)),
                GameAction("study", "讀書", "提升智力，較容易觸發考試、研究、解謎事件。", mapOf("intelligence" to 6, "health" to -2)),
                GameAction("exercise", "運動", "提升體力，較容易觸發身體、反應、戶外事件。", mapOf("health" to 8, "charisma" to 2)),
                GameAction("online", "上網", "增加資訊與偶遇，較容易觸發投資、社群、奇遇事件。", mapOf("intelligence" to 3, "luck" to 2, "health" to -2)),
                GameAction("travel", "旅遊", "花費金錢換取見聞，較容易觸發冒險與人際事件。", mapOf("wealth" to -15, "luck" to 5, "charisma" to 3))
            )
        }
    }

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
            bgmEnabled.collect { soundManager.isBgmEnabled = it }
        }
        viewModelScope.launch {
            sfxVolume.collect { soundManager.sfxVolume = it }
        }
        viewModelScope.launch {
            sfxEnabled.collect { soundManager.isSfxEnabled = it }
        }
        viewModelScope.launch {
            vibrationEnabled.collect { soundManager.isVibrationEnabled = it }
        }
    }

    fun startAppBgm() {
        soundManager.playBgm()
    }

    fun stopAppBgm() {
        soundManager.stopBgm()
    }

    fun playMenuBgm() {
        soundManager.playBgm("main_menu")
    }

    override fun onCleared() {
        soundManager.release()
        super.onCleared()
    }

    fun setBgmVolume(volume: Float) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.BGM_VOLUME, volume) }
    fun setSfxVolume(volume: Float) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.SFX_VOLUME, volume) }

    fun toggleBgm(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.BGM_ENABLED, enabled) }
    fun toggleSfx(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.SFX_ENABLED, enabled) }
    fun toggleVibration(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.VIBRATION_ENABLED, enabled) }
    fun toggleApi(enabled: Boolean) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.API_ENABLED, enabled) }
    fun setApiKey(value: String) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.API_KEY, value) }
    fun setApiEndpoint(value: String) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.API_ENDPOINT, value) }
    fun setApiModel(value: String) = viewModelScope.launch { achievementPrefs.setSetting(AchievementPreferences.API_MODEL, value) }

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
                forcedNextEventId = forcedNextEventId,
                recentEventIds = recentEventIds.toList(),
                lifeLog = _lifeLog.value,
                finalLifeStory = _finalLifeStory.value
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
        recentEventIds.clear()
        recentEventIds.addAll(saveData.recentEventIds.takeLast(recentEventCooldownSize))
        _lifeLog.value = saveData.lifeLog
        _finalLifeStory.value = saveData.finalLifeStory
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
            health = (100 + (allocated["health"] ?: 0)).coerceIn(0, 100),
            intelligence = randomInt.coerceIn(-200, 200),
            charisma = randomCha.coerceIn(-200, 200),
            luck = randomLuck.coerceIn(-200, 200),
            morality = randomMorality.coerceIn(-200, 200),
            wealth = startWealth.coerceIn(-10000, 10000),
            familyBackground = startBackground,
            worldview = _selectedWorldview.value, // 寫入玩家選擇的世界觀
            flags = mutableSetOf()
        )
        soundManager.playBgm(_selectedWorldview.value)
        _isGameOver.value = false
        forcedNextEventId = null
        recentEventIds.clear()
        _lifeLog.value = emptyList()
        _finalLifeStory.value = ""
        _lastDiceRoll.value = null
        _miniGameState.value = null

        _eventResultText.value = generateBirthReport(_characterState.value)
        _currentEvent.value = null
    }

    private fun generateBirthReport(char: Character): String {
        val worldIntro = when (char.worldview) {
            "cultivation" -> "你出生在靈氣未絕的修真世界。遠山常有劍光掠過，鎮上的老人說，人若有根骨，便不必只活一世凡塵。"
            "superpower" -> "你出生在異能逐漸覺醒的時代。新聞裡偶爾會出現違反常識的事故，而大人們總是很快轉開話題。"
            else -> "你出生在霓虹、車流與日常瑣事交織的現代都市。這裡沒有註定的傳說，只有一個又一個必須親手做出的選擇。"
        }
        val wealthDesc = when (char.familyBackground) {
            "富裕" -> "你的家境寬裕，從小不缺資源，也更早看見世界運轉的另一面。"
            "貧窮" -> "你的家庭並不寬裕，許多願望都要等，許多東西都得靠自己爭取。"
            else -> "你的家庭普通而安穩，沒有太多光環，也沒有太多退路。"
        }
        val traitLines = listOfNotNull(
            when {
                char.health >= 130 -> "你哭聲洪亮，身體底子好得讓長輩安心。"
                char.health <= 70 -> "你幼時體弱，連睡夢裡的呼吸都讓人牽掛。"
                else -> null
            },
            when {
                char.intelligence > 50 -> "你很早就會觀察大人的表情，像是在心裡悄悄記下一切規則。"
                char.intelligence < -50 -> "你學東西慢一些，常常要比別人多試幾次才抓到要領。"
                else -> null
            },
            when {
                char.charisma > 50 -> "你天生討人喜歡，陌生人也容易對你放低聲音。"
                char.charisma < -50 -> "你不太擅長親近人群，世界對你來說總隔著一層薄霧。"
                else -> null
            },
            when {
                char.luck > 50 -> "許多小意外總會在最後一刻偏向你，像有看不見的手輕輕扶了一把。"
                char.luck < -50 -> "你似乎常撞上倒楣的巧合，因此很早就學會把事情多想一步。"
                else -> null
            },
            when {
                char.morality > 50 -> "你對別人的痛苦格外敏感，還不懂道理時，就已經懂得不忍心。"
                char.morality < -50 -> "你很早就明白，規則不一定會保護弱者，也不一定非得被遵守。"
                else -> null
            }
        )
        val traitText = if (traitLines.isEmpty()) {
            "你的開局沒有明顯的天賦或缺陷，一切都還藏在未來。"
        } else {
            traitLines.joinToString("\n")
        }

        return """
            【出生介紹】
            $worldIntro

            $wealthDesc
            $traitText

            你的初始壽命上限為 ${formatAge(char.maxAgeMonths)}。從現在開始，每三個月的一次行動，都會把人生推向不同方向。
        """.trimIndent()
    }

    fun performAction(actionKey: String) {
        val action = getAvailableActions().firstOrNull { it.key == actionKey } ?: return
        val currentChar = _characterState.value

        if (currentChar.health <= 0 || currentChar.age >= currentChar.maxAgeMonths) {
            finishGame(currentChar)
            return
        }

        var updatedChar = currentChar.copy(age = currentChar.age + 3)
        val statLog = mutableListOf<String>()
        action.statChanges.forEach { (stat, change) ->
            updatedChar = applyStatChange(updatedChar, stat, change)
            val sign = if (change >= 0) "+" else ""
            statLog.add("${statDisplayName(stat)} $sign$change")
        }

        _characterState.value = updatedChar
        _eventResultText.value = "你花了三個月${action.label}。\n(${statLog.joinToString(", ")})"
        _lastDiceRoll.value = null

        if (updatedChar.health <= 0 || updatedChar.age >= updatedChar.maxAgeMonths) {
            finishGame(updatedChar)
            return
        }

        triggerRandomEvent(action.key)
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
                    setCurrentEvent(forcedEvent)
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

    private fun triggerRandomEvent(actionTag: String? = null) {
        val currentChar = _characterState.value

        viewModelScope.launch {
            if (forcedNextEventId != null) {
                val forcedEvent = repository.getEventById(forcedNextEventId!!)
                if (forcedEvent != null) {
                    setCurrentEvent(forcedEvent)
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

            var availableEventsWithWeights = repository.getAvailableEvents(
                currentAge = currentChar.age / 12,
                currentStats = statsMap,
                currentFlags = currentChar.flags,
                playerWorldview = currentChar.worldview,
                actionTag = actionTag,
                excludedEventIds = recentEventIds.toSet()
            )

            if (availableEventsWithWeights.isEmpty() && actionTag != null) {
                availableEventsWithWeights = repository.getAvailableEvents(
                    currentAge = currentChar.age / 12,
                    currentStats = statsMap,
                    currentFlags = currentChar.flags,
                    playerWorldview = currentChar.worldview,
                    actionTag = null,
                    excludedEventIds = recentEventIds.toSet()
                )
            }

            if (availableEventsWithWeights.isNotEmpty()) {
                val totalWeight = availableEventsWithWeights.sumOf { it.second }
                var randomValue = Math.random() * totalWeight

                for (pair in availableEventsWithWeights) {
                    randomValue -= pair.second
                    if (randomValue <= 0) {
                        setCurrentEvent(pair.first)
                        break
                    }
                }
            } else {
                _currentEvent.value = null
            }
        }
    }

    // ===== 更新：確保玩家的選擇會真的改變屬性 =====
    // ===== 更新：根據成功/失敗套用不同的屬性變化 =====
    private fun setCurrentEvent(event: Event) {
        _currentEvent.value = event
        rememberTriggeredEvent(event.id)
        appendLifeLog("${formatAge(_characterState.value.age)}，觸發事件「${event.title}」。")
        _miniGameState.value = if (event.eventType == "mini_game") {
            MiniGameState(
                eventId = event.id,
                target = (1..10).random(),
                attemptsRemaining = 3,
                message = "猜一個 1 到 10 的數字。你有 3 次機會。"
            )
        } else {
            null
        }
    }

    private fun rememberTriggeredEvent(eventId: String) {
        recentEventIds.remove(eventId)
        recentEventIds.addLast(eventId)
        while (recentEventIds.size > recentEventCooldownSize) {
            recentEventIds.removeFirst()
        }
    }

    fun formatAge(months: Int): String {
        val years = months / 12
        val remainingMonths = months % 12
        return if (remainingMonths == 0) "${years}歲" else "${years}歲${remainingMonths}個月"
    }

    private fun statDisplayName(stat: String): String = when (stat) {
        "health" -> "體力"
        "intelligence" -> "智力"
        "charisma" -> "魅力"
        "luck" -> "運氣"
        "morality" -> "道德"
        "wealth" -> "財富"
        "maxAgeMonths", "lifespan" -> "壽命上限"
        else -> stat
    }

    private fun applyStatChange(character: Character, stat: String, change: Int): Character {
        return when (stat) {
            "health" -> character.copy(health = (character.health + change).coerceIn(0, 100))
            "intelligence" -> character.copy(intelligence = (character.intelligence + change).coerceIn(-200, 200))
            "charisma" -> character.copy(charisma = (character.charisma + change).coerceIn(-200, 200))
            "luck" -> character.copy(luck = (character.luck + change).coerceIn(-200, 200))
            "morality" -> character.copy(morality = (character.morality + change).coerceIn(-200, 200))
            "wealth" -> character.copy(wealth = (character.wealth + change).coerceIn(-10000, 10000))
            "maxAgeMonths", "lifespan" -> character.copy(maxAgeMonths = (character.maxAgeMonths + change).coerceIn(12, 500 * 12))
            else -> character
        }
    }

    private fun appendLifeLog(entry: String) {
        _lifeLog.value = (_lifeLog.value + entry).takeLast(80)
    }

    private fun finishGame(char: Character) {
        _isGameOver.value = true
        checkAndUnlockAchievements(char)
        _finalLifeStory.value = generateLocalLifeStory(char)
        viewModelScope.launch {
            if (apiEnabled.value && apiKey.value.isNotBlank()) {
                val aiStory = runCatching { generateAiLifeStory(char) }.getOrNull()
                if (!aiStory.isNullOrBlank()) {
                    _finalLifeStory.value = aiStory
                }
            }
        }
    }

    private fun generateLocalLifeStory(char: Character): String {
        val ending = if (char.health <= 0) "生命在${formatAge(char.age)}戛然而止。" else "你走到了${formatAge(char.age)}，故事暫告一段落。"
        val importantEvents = _lifeLog.value
            .filter { it.contains("觸發事件") || it.contains("結果：") }
            .takeLast(8)
        val chapters = if (importantEvents.isEmpty()) {
            "　　你的一生沒有留下太多驚心動魄的篇章，更多是在安穩與沉默中慢慢走完。"
        } else {
            importantEvents.joinToString("\n") { "　　$it" }
        }
        return """
            $ending

            人生總結：

            $chapters

            最終屬性：體力 ${char.health}，財富 ${char.wealth}，智力 ${char.intelligence}，魅力 ${char.charisma}，道德 ${char.morality}，運氣 ${char.luck}。
        """.trimIndent()
    }

    private suspend fun generateAiLifeStory(char: Character): String = withContext(Dispatchers.IO) {
        val prompt = """
            請用繁體中文，把以下人生模擬紀錄改寫成有小說感的死亡或結局回顧。
            不要列點，請像短篇傳記一樣寫，約 250 字，只保留關鍵事件。
            年齡：${formatAge(char.age)}
            最終屬性：體力 ${char.health}, 財富 ${char.wealth}, 智力 ${char.intelligence}, 魅力 ${char.charisma}, 道德 ${char.morality}, 運氣 ${char.luck}
            事件紀錄：
            ${_lifeLog.value.joinToString("\n")}
        """.trimIndent()
        val body = mapOf(
            "model" to apiModel.value,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "你是遊戲結局傳記作家，擅長溫柔、具畫面感的繁體中文敘事。"),
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.8
        )
        val connection = (URL(apiEndpoint.value).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${apiKey.value}")
        }
        connection.outputStream.use { it.write(gson.toJson(body).toByteArray(Charsets.UTF_8)) }
        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = gson.fromJson(response, Map::class.java)
        val choices = json["choices"] as? List<*> ?: return@withContext ""
        val first = choices.firstOrNull() as? Map<*, *> ?: return@withContext ""
        val message = first["message"] as? Map<*, *> ?: return@withContext ""
        message["content"] as? String ?: ""
    }
    fun makeChoice(choiceIndex: Int) {
        val event = _currentEvent.value ?: return
        val choices = event.choices ?: return
        if (choiceIndex >= choices.size) return

        val choice = choices[choiceIndex]
        var updatedChar = _characterState.value
        soundManager.playClickSound()
        soundManager.vibrate()

        var diceLog = ""
        val isSuccess = if (choice.failResultText.isNotEmpty()) {
            val currentLuck = updatedChar.luck
            val luckModifier = currentLuck / 10
            val successTarget = (70 + luckModifier).coerceIn(5, 95)
            val diceRoll = (1..100).random()
            diceLog = "\n(判定: d100=$diceRoll, 目標<=$successTarget, 幸運修正=$luckModifier)"
            _lastDiceRoll.value = DiceRollResult(
                roll = diceRoll,
                target = successTarget,
                luckModifier = luckModifier,
                success = diceRoll <= successTarget
            )
            diceRoll <= successTarget
        } else {
            _lastDiceRoll.value = null
            true
        }

        val actionResultText = if (isSuccess) choice.successResultText else choice.failResultText
        val currentStatChanges = if (isSuccess) choice.statChanges else choice.failStatChanges
        val currentAddFlag = if (isSuccess) choice.addFlag else choice.failAddFlag

        val statLog = mutableListOf<String>()
        currentStatChanges?.forEach { (stat, change) ->
            updatedChar = applyStatChange(updatedChar, stat, change)
            val sign = if (change >= 0) "+" else ""
            statLog.add("${statDisplayName(stat)} $sign$change")
        }

        currentAddFlag?.let { flag ->
            val updatedFlags = updatedChar.flags.toMutableSet().apply { add(flag) }
            updatedChar = updatedChar.copy(flags = updatedFlags)
        }

        _characterState.value = updatedChar

        if (!choice.nextEventId.isNullOrEmpty()) {
            forcedNextEventId = choice.nextEventId
        }

        val logString = if (statLog.isNotEmpty()) "\n(變化：${statLog.joinToString(", ")})" else ""
        _eventResultText.value = "【抉擇結果】\n$actionResultText$diceLog$logString"
        appendLifeLog("${formatAge(updatedChar.age)}，在「${event.title}」中選擇「${choice.choiceText}」，結果：$actionResultText")

        if (updatedChar.health <= 0 || updatedChar.flags.contains("dead")) {
            finishGame(updatedChar)
            return
        }

        if (!choice.nextEventId.isNullOrEmpty()) {
            triggerRandomEvent()
        } else {
            _currentEvent.value = null
        }
    }

    fun submitMiniGameGuess(guess: Int) {
        val state = _miniGameState.value ?: return
        val event = _currentEvent.value ?: return
        val choice = event.choices?.firstOrNull() ?: return

        if (guess == state.target) {
            _miniGameState.value = null
            applyResolvedChoice(event, choice, isSuccess = true, prefix = "你猜中了數字 $guess。\n")
            return
        }

        val attemptsLeft = state.attemptsRemaining - 1
        if (attemptsLeft > 0) {
            _miniGameState.value = state.copy(
                attemptsRemaining = attemptsLeft,
                message = "不是 $guess。還剩 $attemptsLeft 次機會。"
            )
        } else {
            _miniGameState.value = null
            applyResolvedChoice(event, choice, isSuccess = false, prefix = "三次都沒猜中，正確數字是 ${state.target}。\n")
        }
    }

    private fun applyResolvedChoice(event: Event, choice: Choice, isSuccess: Boolean, prefix: String = "") {
        var updatedChar = _characterState.value
        soundManager.playClickSound()
        soundManager.vibrate()
        _lastDiceRoll.value = null

        val actionResultText = if (isSuccess) choice.successResultText else choice.failResultText
        val currentStatChanges = if (isSuccess) choice.statChanges else choice.failStatChanges
        val currentAddFlag = if (isSuccess) choice.addFlag else choice.failAddFlag
        val statLog = mutableListOf<String>()

        currentStatChanges?.forEach { (stat, change) ->
            updatedChar = applyStatChange(updatedChar, stat, change)
            val sign = if (change >= 0) "+" else ""
            statLog.add("${statDisplayName(stat)} $sign$change")
        }

        currentAddFlag?.let { flag ->
            val updatedFlags = updatedChar.flags.toMutableSet().apply { add(flag) }
            updatedChar = updatedChar.copy(flags = updatedFlags)
        }

        _characterState.value = updatedChar

        if (!choice.nextEventId.isNullOrEmpty()) {
            forcedNextEventId = choice.nextEventId
        }

        val logString = if (statLog.isNotEmpty()) "\n(變化：${statLog.joinToString(", ")})" else ""
        _eventResultText.value = "【小遊戲結果】\n$prefix$actionResultText$logString"
        appendLifeLog("${formatAge(updatedChar.age)}，在「${event.title}」小遊戲中${if (isSuccess) "成功" else "失敗"}，結果：$actionResultText")

        if (updatedChar.health <= 0 || updatedChar.flags.contains("dead")) {
            finishGame(updatedChar)
            return
        }

        if (!choice.nextEventId.isNullOrEmpty()) {
            triggerRandomEvent()
        } else {
            _currentEvent.value = null
        }
    }

    private fun checkAndUnlockAchievements(char: Character) {
        viewModelScope.launch {
            suspend fun unlockWhen(condition: Boolean, achievementName: String) {
                if (condition) achievementPrefs.unlockAchievement(achievementName)
            }

            fun hasAnyFlag(vararg flags: String): Boolean = flags.any { char.flags.contains(it) }
            fun hasFlagContaining(text: String): Boolean = char.flags.any { it.contains(text) }
            fun hasFlagEnding(suffix: String): Boolean = char.flags.any { it.endsWith(suffix) }

            // 屬性極端類
            unlockWhen(char.wealth > 1000, "商業奇才")
            unlockWhen(char.wealth >= 3000, "財務自由")
            unlockWhen(char.wealth < -50, "窮神附體")
            unlockWhen(char.wealth <= -500, "負債人生")
            unlockWhen(char.intelligence > 100, "學術泰斗")
            unlockWhen(char.intelligence >= 150, "人間資料庫")
            unlockWhen(char.morality < -100, "法外狂徒")
            unlockWhen(char.morality <= -150, "深淵回望")
            unlockWhen(char.morality > 100, "聖人之心")
            unlockWhen(char.morality >= 150, "功德無量")
            unlockWhen(char.luck > 90, "天選之人")
            unlockWhen(char.luck >= 150, "命運寵兒")
            unlockWhen(char.luck <= -80, "厄運纏身")
            unlockWhen(char.luck <= -150, "黑色星期")
            unlockWhen(char.charisma > 90, "萬人迷")
            unlockWhen(char.charisma >= 150, "眾星拱月")
            unlockWhen(char.charisma <= -80, "透明人")
            unlockWhen(char.health >= 100, "鋼鐵之軀")
            unlockWhen(char.health in 1..19, "病骨支離")

            // 年齡與生死類
            unlockWhen(char.age >= 18 * 12, "成年禮")
            unlockWhen(char.age >= 30 * 12, "而立之年")
            unlockWhen(char.age >= 40 * 12, "不惑之路")
            unlockWhen(char.age >= 50 * 12, "知命之年")
            unlockWhen(char.age >= 60 * 12, "花甲之歲")
            unlockWhen(char.age >= 70 * 12, "古稀人生")
            unlockWhen(char.age >= 80 * 12, "耄耋長者")
            unlockWhen(char.age >= 90 * 12, "鮐背之年")
            unlockWhen(char.age >= 100 * 12, "世紀人瑞")
            unlockWhen(char.age >= 120 * 12, "百二長生")
            unlockWhen(char.age >= 150 * 12, "一百五十年")
            unlockWhen(char.health <= 0 && char.age <= 18 * 12, "英年早逝")
            unlockWhen(char.health > 0 && char.flags.any { it.contains("death") || it.contains("illness") || it.contains("accident") || it.contains("overload") || it.contains("attack") }, "危機倖存者")
            unlockWhen(char.flags.contains("拯救世界"), "救世主")

            // 世界觀類
            unlockWhen(char.worldview == "urban", "都市居民")
            unlockWhen(char.worldview == "cultivation", "修仙初聞")
            unlockWhen(char.worldview == "superpower", "異能覺醒")
            unlockWhen(char.worldview == "urban" && char.age >= 60 * 12, "都市求生者")
            unlockWhen(char.worldview == "cultivation" && char.age >= 100 * 12, "山中長修")
            unlockWhen(char.worldview == "superpower" && char.age >= 70 * 12, "異能老兵")
            unlockWhen(char.maxAgeMonths > 150 * 12, "壽元延展")

            // 都市事件類
            unlockWhen(hasAnyFlag("library_card"), "圖書館的門")
            unlockWhen(hasAnyFlag("night_school_done"), "夜校燈光")
            unlockWhen(hasAnyFlag("senior_consultant_done"), "晚年顧問")
            unlockWhen(hasAnyFlag("old_house_resolved"), "老屋抉擇")
            unlockWhen(hasAnyFlag("senior_marathon_done"), "銀髮跑者")
            unlockWhen(hasAnyFlag("urban_senior_phone_scam_done"), "防詐達人")
            unlockWhen(hasAnyFlag("urban_memory_cafe_done"), "城市紀念照")

            // 修仙事件類
            unlockWhen(hasAnyFlag("cultivation_spirit_root_test_done"), "靈根初現")
            unlockWhen(hasAnyFlag("cultivation_outer_sect_chore_done"), "外門苦修")
            unlockWhen(hasAnyFlag("cultivation_secret_realm_entered"), "秘境行者")
            unlockWhen(hasAnyFlag("cultivation_tribulation_cloud_done"), "渡劫未死")
            unlockWhen(hasAnyFlag("cultivation_sect_elder_seat_done"), "宗門長老")
            unlockWhen(hasAnyFlag("cultivation_ancient_immortal_tomb_done"), "古墓問心")
            unlockWhen(hasAnyFlag("cultivation_ascension_trace_done"), "飛升線索")

            // 超能力事件類
            unlockWhen(hasAnyFlag("ability_awakened", "hidden_power"), "能力初醒")
            unlockWhen(hasAnyFlag("trained_control"), "地下訓練")
            unlockWhen(hasAnyFlag("street_hero"), "匿名英雄")
            unlockWhen(hasAnyFlag("agency_member"), "管理局成員")
            unlockWhen(hasAnyFlag("masked_vigilante"), "面罩夜巡")
            unlockWhen(hasAnyFlag("metro_savior"), "地鐵救星")
            unlockWhen(hasAnyFlag("rift_sealer"), "裂縫封印者")
            unlockWhen(hasAnyFlag("superpower_elder_power_teacher_done"), "異能教師")
            unlockWhen(hasAnyFlag("superpower_elder_archive_done"), "口述史見證者")

            // 收集與流程類
            unlockWhen(char.flags.size >= 5, "人生起步")
            unlockWhen(char.flags.size >= 15, "事件收藏家")
            unlockWhen(char.flags.size >= 30, "命運編年史")
            unlockWhen(hasFlagContaining("after") || hasFlagContaining("followup") || hasFlagContaining("trace") || hasFlagContaining("meeting"), "多線人生")
            unlockWhen(hasFlagEnding("_done") && char.flags.any { flag ->
                flag.contains("game") || flag.contains("minigame") || flag.contains("guess") ||
                    flag.contains("test") || flag.contains("trial") || flag.contains("drill")
            }, "小遊戲新手")
            val miniGameDoneCount = char.flags.count { flag ->
                flag.contains("game") || flag.contains("minigame") || flag.contains("guess") ||
                    flag.contains("test") || flag.contains("trial") || flag.contains("drill")
            }
            unlockWhen(miniGameDoneCount >= 5, "小遊戲高手")

            // 如果活到老且屬性都很平庸 (無一超過 50，也沒低於 -50)
            if (char.age >= 60 * 12 &&
                char.wealth in -50..50 && char.intelligence in -50..50 &&
                char.morality in -50..50 && char.luck in -50..50) {
                achievementPrefs.unlockAchievement("平凡是福")
            }
        }
    }
}



package com.example.lifechoicesimulator.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.lifechoicesimulator.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifechoicesimulator.ui.theme.LifeChoiceSimulatorTheme
import com.example.lifechoicesimulator.viewmodel.GameViewModel
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import com.example.lifechoicesimulator.model.AchievementRegistry
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeChoiceSimulatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LifeChoiceApp(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startAppBgm()
    }

    override fun onStop() {
        viewModel.stopAppBgm()
        super.onStop()
    }
}

@Composable
fun LifeChoiceApp(viewModel: GameViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "loading") {
        composable("loading") {
            LoadingScreen(
                onNavigateToMenu = {
                    viewModel.playMenuBgm() // 點擊時才開始播放 BGM
                    navController.navigate("main_menu") {
                        popUpTo("loading") { inclusive = true }
                    }
                }
            )
        }
        composable("main_menu") {
            MainMenuScreen(
                viewModel = viewModel,
                onStartGame = {
                    viewModel.prepareNewGame() // 準備結算可用點數
                    navController.navigate("allocation")
                },
                onLoadGameComplete = {
                    navController.navigate("game") {
                        popUpTo("main_menu") { inclusive = false }
                    }
                },
                onSettings = { navController.navigate("settings") },
                onAchievements = { navController.navigate("achievements") },
                onOnline = { navController.navigate("online") }
            )
        }

        composable("allocation") {
            AllocationScreen(viewModel = viewModel, onConfirm = {
                navController.navigate("game") {
                    popUpTo("main_menu") { inclusive = false } // 避免按返回鍵回到分配畫面
                }
            })
        }

        composable("game") {
            Scaffold(
                topBar = { GameTopMenu(viewModel, navController) }
            ) { paddingValues ->
                GameScreen(viewModel, navController, Modifier.padding(paddingValues))
            }
        }

        composable("summary") {
            SummaryScreen(viewModel, onRestart = {
                viewModel.prepareNewGame()
                navController.navigate("allocation") {
                    popUpTo("summary") { inclusive = true }
                }
            }, onMainMenu = {
                navController.navigate("main_menu") {
                    popUpTo("summary") { inclusive = true }
                }
            })
        }

        composable("achievements") {
            AchievementScreen(viewModel = viewModel, onBack = {
                navController.popBackStack()
            })
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("online") {
            OnlineScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ===== 登入畫面 =====
@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onLoadGameComplete: () -> Unit,
    onSettings: () -> Unit,
    onAchievements: () -> Unit,
    onOnline: () -> Unit
) {
    var showLoadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("人生選擇模擬器", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = onStartGame, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("開始新人生")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 讀取舊人生按鈕
        Button(
            onClick = { showLoadDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("讀取舊人生")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onAchievements, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("多周目成就與解鎖")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onOnline, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("聯網模式")
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("音效與設定")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { (context as? Activity)?.finish() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("離開遊戲", color = Color.Gray) // 使用灰色讓它看起來像是次要操作
        }
    }

    // 讀檔彈窗
    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("選擇進度載入") },
            text = {
                Column {
                    listOf(1, 2, 3).forEach { slotId ->
                        val slotState by viewModel.getSaveSlotData(slotId).collectAsState(initial = null)

                        OutlinedButton(
                            onClick = {
                                slotState?.let {
                                    viewModel.loadGameFromSlot(slotId, it)
                                    showLoadDialog = false
                                    onLoadGameComplete()
                                }
                            },
                            enabled = slotState != null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            if (slotState != null) {
                                val char = slotState!!.character
                                Text("進度 $slotId：${viewModel.formatAge(char.age)} | 體力:${char.health} | 世界:${char.worldview}")
                            } else {
                                Text("進度 $slotId：無存檔資料")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text("關閉") }
            }
        )
    }
}

// ===== 點數分配畫面 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllocationScreen(viewModel: GameViewModel, onConfirm: () -> Unit) {
    val allocatablePoints by viewModel.allocatablePoints.collectAsState()
    val allocatedStats by viewModel.allocatedStats.collectAsState()
    val currentWorldview by viewModel.selectedWorldview.collectAsState()

    val statLabels = listOf(
        "health" to "體力",
        "intelligence" to "智力",
        "charisma" to "魅力",
        "luck" to "運氣",
        "morality" to "道德",
        "wealth" to "財富"
    )
    val worldviews = listOf("urban" to "現代都市", "cultivation" to "東方仙俠", "superpower" to "超能力")

    // 👉 修正：將所有 UI 元件包進同一個 Column 中，避免排版重疊
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("選擇轉生世界", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            worldviews.forEach { (key, label) ->
                FilterChip(
                    selected = currentWorldview == key,
                    onClick = { viewModel.selectWorldview(key) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("初始屬性分配", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "剩餘可分配點數: $allocatablePoints",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                statLabels.forEach { (statKey, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, fontSize = 18.sp, modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.allocatePoint(statKey, false) },
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("-") }

                            Text(
                                text = "${allocatedStats[statKey]}",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedButton(
                                onClick = { viewModel.allocatePoint(statKey, true) },
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("+") }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.confirmAndStartGame()
                onConfirm()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (allocatablePoints > 0) "保留點數並開始" else "確認並開始遊戲")
        }
    }
}

// ===== 頂部目錄 (TopAppBar) =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopMenu(viewModel: GameViewModel, navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    var showExitPrompt by remember { mutableStateOf(false) }
    var showSaveSlotSelector by remember { mutableStateOf(false) }

    val isAiMode by viewModel.isAiModeEnabled.collectAsState()

    TopAppBar(
        title = { Text("遊戲進行中") },
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "目錄選單")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("返回主畫面") },
                    onClick = {
                        expanded = false
                        showExitPrompt = true // 跳出詢問存檔彈窗
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isAiMode) "切換為：預設 JSON 事件" else "切換為：AI 生成事件") },
                    onClick = {
                        expanded = false
                        viewModel.toggleAiMode()
                    }
                )
            }
        }
    )

    // 彈窗 A：詢問是否存檔
    if (showExitPrompt) {
        AlertDialog(
            onDismissRequest = { showExitPrompt = false },
            title = { Text("離開遊戲") },
            text = { Text("您即將離開遊戲，是否要儲存當前進度？") },
            confirmButton = {
                Button(onClick = {
                    showExitPrompt = false
                    showSaveSlotSelector = true // 開啟存檔位置選擇
                }) { Text("儲存並退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitPrompt = false
                        navController.navigate("main_menu") {
                            popUpTo("main_menu") { inclusive = true }
                        }
                    }) { Text("直接退出") }

                    TextButton(onClick = { showExitPrompt = false }) { Text("取消") }
                }
            }
        )
    }

    // 彈窗 B：選擇要覆蓋或儲存的 Slot
    if (showSaveSlotSelector) {
        AlertDialog(
            onDismissRequest = { showSaveSlotSelector = false },
            title = { Text("選擇儲存位置") },
            text = {
                Column {
                    listOf(1, 2, 3).forEach { slotId ->
                        val slotState by viewModel.getSaveSlotData(slotId).collectAsState(initial = null)
                        val btnText = if (slotState != null) "覆蓋進度 $slotId" else "儲存至進度 $slotId (空)"

                        OutlinedButton(
                            onClick = {
                                viewModel.saveCurrentGame(slotId) {
                                    showSaveSlotSelector = false
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) { Text(btnText) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveSlotSelector = false }) { Text("取消") }
            }
        )
    }
}

// ===== 遊戲主畫面 =====
@Composable
fun GameScreen(viewModel: GameViewModel, navController: NavController, modifier: Modifier = Modifier) {
    val character by viewModel.characterState.collectAsState()
    val currentEvent by viewModel.currentEvent.collectAsState()
    val resultText by viewModel.eventResultText.collectAsState()
    val isGameOver by viewModel.isGameOver.collectAsState()
    val lastDiceRoll by viewModel.lastDiceRoll.collectAsState()
    val miniGameState by viewModel.miniGameState.collectAsState()

    LaunchedEffect(isGameOver) {
        if (isGameOver) {
            navController.navigate("summary") {
                popUpTo("game") { inclusive = true }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            text = """
                時間: ${viewModel.formatAge(character.age)} | 體力: ${character.health} | 財富: ${character.wealth}
                智力: ${character.intelligence} | 魅力: ${character.charisma}
                道德: ${character.morality} | 運氣: ${character.luck}
            """.trimIndent(),
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (resultText.isNotEmpty()) {
            Text(text = resultText, color = Color(0xFF006600), fontStyle = FontStyle.Italic)
            Spacer(modifier = Modifier.height(16.dp))
        }

        lastDiceRoll?.let { roll ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("投骰判定", fontWeight = FontWeight.Bold)
                    Text("d100: ${roll.roll} / 目標: ${roll.target} / 幸運修正: ${roll.luckModifier}")
                    LinearProgressIndicator(
                        progress = { (roll.roll / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        if (roll.success) "判定成功" else "判定失敗",
                        color = if (roll.success) Color(0xFF006600) else Color(0xFFAA0000)
                    )
                }
            }
        }

        if (currentEvent == null) {
            Text(text = "選擇接下來三個月的行動", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            viewModel.getAvailableActions().forEach { action ->
                OutlinedButton(
                    onClick = { viewModel.performAction(action.key) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(action.label, fontWeight = FontWeight.Bold)
                        Text(action.description, fontSize = 12.sp)
                    }
                }
            }
        }

        currentEvent?.let { event ->
            if (event.eventType == "mini_game") {
                AssistChip(onClick = {}, label = { Text("小遊戲事件") }, modifier = Modifier.padding(bottom = 8.dp))
            } else if (event.eventType == "chain") {
                AssistChip(onClick = {}, label = { Text("連續事件") }, modifier = Modifier.padding(bottom = 8.dp))
            } else if (event.eventType == "special") {
                AssistChip(onClick = {}, label = { Text("特殊事件") }, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(text = event.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = event.description, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (event.eventType == "mini_game" && miniGameState != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val state = miniGameState!!
                        val gameTitle = when (state.gameType) {
                            "sequence" -> "記憶順序小遊戲"
                            "reaction" -> "三擇反應小遊戲"
                            else -> "猜數字小遊戲"
                        }
                        Text(gameTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(state.message)
                        Spacer(modifier = Modifier.height(12.dp))
                        when (state.gameType) {
                            "sequence" -> {
                                Text("可選符號", fontSize = 12.sp, color = Color.Gray)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (1..4).forEach { symbol ->
                                        OutlinedButton(
                                            onClick = { viewModel.submitMiniGameSequence(symbol) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(symbol.toString())
                                        }
                                    }
                                }
                            }
                            "reaction" -> {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1 to "左", 2 to "中", 3 to "右").forEach { (value, label) ->
                                        OutlinedButton(
                                            onClick = { viewModel.submitMiniGameReaction(value) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                            else -> {
                                (1..10).chunked(5).forEach { rowNumbers ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowNumbers.forEach { number ->
                                            OutlinedButton(
                                                onClick = { viewModel.submitMiniGameGuess(number) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(number.toString())
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                event.choices?.forEachIndexed { index, choice ->
                    OutlinedButton(
                        onClick = { viewModel.makeChoice(index) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(choice.choiceText)
                    }
                }
            }
        }
    }
}
// ===== 人生總結畫面 =====
@Composable
fun SummaryScreen(viewModel: GameViewModel, onRestart: () -> Unit, onMainMenu: () -> Unit) {
    val character by viewModel.characterState.collectAsState()
    val finalLifeStory by viewModel.finalLifeStory.collectAsState()
    val apiStatus by viewModel.apiStatus.collectAsState()

    val title = when {
        character.wealth > 80 -> "華爾街巨擘"
        character.intelligence > 80 -> "學術泰斗"
        character.morality < -50 -> "法外狂徒"
        else -> "平庸的普通人"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("人生落幕", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("【終局結算報告】", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("最終時間: ${viewModel.formatAge(character.age)}")
                Text("獲得稱號: $title", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(finalLifeStory.ifBlank { "人生回顧生成中..." }, fontStyle = FontStyle.Italic)
                Spacer(modifier = Modifier.height(8.dp))
                Text(apiStatus, fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text("轉生重啟")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onMainMenu, modifier = Modifier.fillMaxWidth()) {
            Text("返回主畫面")
        }
    }
}
// ===== 成就展示畫面 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    // 取得玩家目前解鎖的成就清單 (Set<String>)
    val unlockedSet by viewModel.unlockedAchievements.collectAsState()
    val allAchievements = AchievementRegistry.allAchievements

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成就圖鑑 (${unlockedSet.size} / ${allAchievements.size})") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回", fontSize = 16.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), // 兩排並列
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allAchievements) { achievement ->
                val isUnlocked = unlockedSet.contains(achievement.id)

                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.85f), // 調整卡片比例
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 未來這裡可以換成 Image(painterResource(id = achievement.iconResId))
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(if (isUnlocked) MaterialTheme.colorScheme.primary else Color.Gray, shape = MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isUnlocked) Color.Yellow else Color.DarkGray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isUnlocked) achievement.title else "未解鎖",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isUnlocked) achievement.description else "達成條件：???",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val bgmEnabled by viewModel.bgmEnabled.collectAsState()
    val sfxEnabled by viewModel.sfxEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val apiEnabled by viewModel.apiEnabled.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiEndpoint by viewModel.apiEndpoint.collectAsState()
    val apiModel by viewModel.apiModel.collectAsState()
    val onlineEnabled by viewModel.onlineEnabled.collectAsState()
    val onlineBaseUrl by viewModel.onlineBaseUrl.collectAsState()
    val onlinePlayerName by viewModel.onlinePlayerName.collectAsState()

    // 👉 取得目前的音量狀態
    val bgmVolume by viewModel.bgmVolume.collectAsState()
    val sfxVolume by viewModel.sfxVolume.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音效與設定") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(paddingValues).padding(24.dp)
        ) {
            // BGM 設定區塊
            SettingsSwitchRow("背景音樂 (BGM)", bgmEnabled) { viewModel.toggleBgm(it) }
            if (bgmEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音量", fontSize = 14.sp, modifier = Modifier.width(48.dp))
                    Slider(
                        value = bgmVolume,
                        onValueChange = { viewModel.setBgmVolume(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // 音效設定區塊
            SettingsSwitchRow("遊戲音效 (SFX)", sfxEnabled) { viewModel.toggleSfx(it) }
            if (sfxEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音量", fontSize = 14.sp, modifier = Modifier.width(48.dp))
                    Slider(
                        value = sfxVolume,
                        onValueChange = { viewModel.setSfxVolume(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // 震動設定
            SettingsSwitchRow("震動回饋", vibrationEnabled) { viewModel.toggleVibration(it) }
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            SettingsSwitchRow("API 敘事生成", apiEnabled) { viewModel.toggleApi(it) }
            if (apiEnabled) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { viewModel.setApiKey(it) },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { viewModel.setApiEndpoint(it) },
                    label = { Text("Endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiModel,
                    onValueChange = { viewModel.setApiModel(it) },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "死亡或結局時會優先使用 API 生成回顧；失敗時自動使用本地日誌敘事。",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            SettingsSwitchRow("聯網模式", onlineEnabled) { viewModel.toggleOnline(it) }
            if (onlineEnabled) {
                OutlinedTextField(
                    value = onlineBaseUrl,
                    onValueChange = { viewModel.setOnlineBaseUrl(it) },
                    label = { Text("伺服器網址，例如 https://example.com/api") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = onlinePlayerName,
                    onValueChange = { viewModel.setOnlinePlayerName(it) },
                    label = { Text("玩家名稱") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "聊天板與人生紀錄分享會連到此伺服器。正式版的 MongoDB、帳號與審核都應放在後端。",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val onlineEnabled by viewModel.onlineEnabled.collectAsState()
    val onlineBaseUrl by viewModel.onlineBaseUrl.collectAsState()
    val onlineStatus by viewModel.onlineStatus.collectAsState()
    val messages by viewModel.onlineMessages.collectAsState()
    val sharedStories by viewModel.onlineSharedStories.collectAsState()
    val finalLifeStory by viewModel.finalLifeStory.collectAsState()
    var messageText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聯網模式") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (onlineEnabled && onlineBaseUrl.isNotBlank()) "已設定伺服器：$onlineBaseUrl" else "尚未設定聯網模式，請先到設定填入伺服器網址。",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.checkOnlineHealth() }, modifier = Modifier.weight(1f)) {
                            Text("健康檢查")
                        }
                        OutlinedButton(onClick = { viewModel.refreshOnlineMessages() }, modifier = Modifier.weight(1f)) {
                            Text("刷新聊天")
                        }
                        OutlinedButton(onClick = { viewModel.refreshOnlineSharedStories() }, modifier = Modifier.weight(1f)) {
                            Text("刷新紀錄")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.shareCurrentLifeStory() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = finalLifeStory.isNotBlank()
                    ) {
                        Text("分享目前人生紀錄")
                    }
                    Text(onlineStatus, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("聊天板", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("輸入訊息") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    viewModel.sendOnlineMessage(messageText)
                    messageText = ""
                }) {
                    Text("送出")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    if (messages.isEmpty()) {
                        Text("尚無聊天訊息。", color = Color.Gray, fontSize = 13.sp)
                    }
                }
                items(messages) { msg ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(msg.playerName, fontWeight = FontWeight.Bold)
                            Text(msg.message)
                            if (msg.createdAt.isNotBlank()) Text(msg.createdAt, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("公開人生紀錄", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (sharedStories.isEmpty()) {
                        Text("尚無公開紀錄。", color = Color.Gray, fontSize = 13.sp)
                    }
                }
                items(sharedStories) { story ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(story.title, fontWeight = FontWeight.Bold)
                            Text("${story.playerName} · ${story.ageText} · ${story.worldview}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(story.story, maxLines = 6)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LoadingScreen(onNavigateToMenu: () -> Unit) {
    var isReady by remember { mutableStateOf(false) }

    // 模擬載入時間 1.5 秒
    LaunchedEffect(Unit) {
        delay(1500)
        isReady = true
    }

    // 文字閃爍動畫
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 當就緒時，點擊整個畫面就會進入選單
            .clickable(enabled = isReady) { onNavigateToMenu() }
    ) {
        // 背景圖片 (替換成你生成的圖片)
         Image(
             painter = painterResource(id = R.drawable.bg_loading),
             contentDescription = "Loading Background",
             modifier = Modifier.fillMaxSize(),
             contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        // 暫時代替圖片的背景顏色
        //Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))

        // 提示文字
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isReady) {
                Text(
                    text = "— 點擊畫面進入遊戲 —",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = alpha)
                )
            } else {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("讀取命運中...", color = Color.White)
            }
        }
    }
}

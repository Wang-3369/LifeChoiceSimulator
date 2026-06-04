package com.example.lifechoicesimulator.model

data class Achievement(
    val id: String,          // 唯一辨識碼 (存在 DataStore 裡的字串)
    val title: String,       // 顯示名稱
    val description: String, // 達成條件或趣味描述
    val iconResId: Int? = null // 預留給未來的圖片資源 (例如 R.drawable.ach_rich)
)

// 成就註冊表：遊戲中所有的成就都在這裡定義
object AchievementRegistry {
    val allAchievements = listOf(
        // 屬性極端類
        Achievement("商業奇才", "商業奇才", "累積超過 1000 點財富。錢不是萬能，但沒錢萬萬不能。"),
        Achievement("窮神附體", "窮神附體", "財富低於 -50。你這輩子大概都在幫別人數錢。"),
        Achievement("學術泰斗", "學術泰斗", "智力突破 100。你的大腦堪比超級電腦。"),
        Achievement("法外狂徒", "法外狂徒", "道德低於 -100。警察局的通緝令上印著你的高清大頭照。"),
        Achievement("天選之人", "天選之人", "運氣突破 90。走路都會撿到金條的幸運兒。"),
        Achievement("萬人迷", "萬人迷", "魅力突破 90。只需要一個眼神，就能讓無數人為你傾倒。"),

        // 生死與事件類
        Achievement("世紀人瑞", "世紀人瑞", "成功活到 100 歲。見證了整整一個世紀的變遷。"),
        Achievement("英年早逝", "英年早逝", "在 18 歲（含）前不幸夭折。下輩子請務必注意安全。"),
        Achievement("救世主", "救世主", "完成特定事件：拯救了這個世界。"),
        Achievement("平凡是福", "平凡是福", "這輩子平平無奇，沒有特別突出的表現。有時候，這也是一種幸福。")
    )
}
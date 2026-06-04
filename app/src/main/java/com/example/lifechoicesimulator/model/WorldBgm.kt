package com.example.lifechoicesimulator.model

import androidx.annotation.RawRes
import com.example.lifechoicesimulator.R

enum class WorldBgm(
    val worldviewId: String,
    val title: String,
    @param:RawRes val resId: Int // 👉 修正：加上 @param: 告訴編譯器這是參數的註解
) {
    MENU("main_menu", "《轉生檔案館》", R.raw.bgm_menu_main),
    FALLBACK("fallback", "《觀測者的凝視》", R.raw.bgm_sys_fallback),
    URBAN("urban", "《霓虹叢林的生存法則》", R.raw.bgm_world_urban),
    CULTIVATION("cultivation", "《太虛幻境》", R.raw.bgm_world_cultivation),
    SUPERPOWER("superpower", "《覺醒前夕》", R.raw.bgm_world_superpower);

    companion object {
        fun fromWorldview(id: String): WorldBgm {
            return entries.find { it.worldviewId == id } ?: FALLBACK
        }
    }
}
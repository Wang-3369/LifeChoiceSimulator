package com.example.lifechoicesimulator.repository

class UserRepository {
    /**
     * 邀請系統與後端 MongoDB 溝通
     */
    suspend fun verifyReferralCode(code: String): Boolean {
        // TODO: 透過 Retrofit 發送請求至後端伺服器驗證邀請碼，並更新解鎖進度 [cite: 122, 123]
        return true
    }
}
package com.example.lifechoicesimulator.model

data class Character(
    var age: Int = 0,
    var intelligence: Int = 0,
    var charisma: Int = 0,
    var health: Int = 100,
    var luck: Int = 0,
    var morality: Int = 0,

    var familyBackground: String = "普通",
    var wealth: Int = 0,
    var talents: List<String> = listOf(),

    // 👉 確保有這兩行：
    var worldview: String = "urban", // 預設給 urban
    var flags: MutableSet<String> = mutableSetOf()
)
package com.example.lifechoicesimulator.model

data class Character(
    var age: Int = 0, // months
    var intelligence: Int = 0,
    var charisma: Int = 0,
    var health: Int = 100,
    var luck: Int = 0,
    var morality: Int = 0,
    var familyBackground: String = "普通",
    var wealth: Int = 0,
    var talents: List<String> = listOf(),
    var maxAgeMonths: Int = 150 * 12,
    var worldview: String = "urban",
    var flags: MutableSet<String> = mutableSetOf()
)

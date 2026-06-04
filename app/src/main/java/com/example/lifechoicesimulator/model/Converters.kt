package com.example.lifechoicesimulator.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EventConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringIntMap(value: Map<String, Int>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringIntMap(value: String): Map<String, Int> {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(value, type) ?: emptyMap()
    }

    @TypeConverter
    fun fromStringDoubleMap(value: Map<String, Double>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringDoubleMap(value: String): Map<String, Double> {
        val type = object : TypeToken<Map<String, Double>>() {}.type
        return gson.fromJson(value, type) ?: emptyMap()
    }

    @TypeConverter
    fun fromChoiceList(value: List<Choice>?): String = gson.toJson(value)

    @TypeConverter
    fun toChoiceList(value: String): List<Choice> {
        val type = object : TypeToken<List<Choice>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
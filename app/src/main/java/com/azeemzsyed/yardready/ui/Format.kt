package com.azeemzsyed.yardready.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting helpers shared by screens and notifications. */
object Format {
    private val dayTime = DateTimeFormatter.ofPattern("EEE h a", Locale.getDefault())
    private val dayDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val shortDay = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dateTime = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

    fun dayTime(time: Instant, zone: ZoneOffset): String = dayTime.format(time.atOffset(zone))
    fun dayDate(date: LocalDate): String = dayDate.format(date)
    fun shortDay(date: LocalDate): String = shortDay.format(date)
    fun dateTime(epochMillis: Long): String = dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    fun cm(value: Double): String =
        if (value >= 10 || value == Math.floor(value)) "${value.toInt()} cm" else String.format(Locale.getDefault(), "%.1f cm", value)

    fun temp(value: Double): String = "${Math.round(value)}°"

    fun conditionEmoji(condition: String): String = when (condition) {
        "Snow" -> "❄️"
        "Rain", "Drizzle" -> "🌧️"
        "Thunderstorm" -> "⛈️"
        "Clouds" -> "☁️"
        "Clear" -> "☀️"
        else -> "🌫️"
    }
}

package com.azeemzsyed.yardready.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One 3-hour block from the OpenWeatherMap 5 day / 3 hour forecast. */
data class ForecastSlot(
    val time: Instant,
    val tempC: Double,
    /** Probability of precipitation, 0.0 to 1.0. */
    val precipProbability: Double,
    /** Rain volume for the 3 hours, in mm. */
    val rainMm: Double,
    /** Snow volume for the 3 hours, in mm of water equivalent. */
    val snowMm: Double,
    val windMs: Double,
    /** e.g. "Clear", "Rain", "Snow" */
    val condition: String,
    val description: String,
)

data class Forecast(
    val locationName: String,
    /** UTC offset of the forecast location (from the API), used for local hours. */
    val zone: ZoneOffset,
    val slots: List<ForecastSlot>,
)

/** What the app recommends for lawn mowing. */
sealed interface MowAdvice {
    data class MowAt(val slot: ForecastSlot, val reasons: List<String>) : MowAdvice
    data class NotNeededYet(val lastMowed: Instant, val nextDue: LocalDate) : MowAdvice
    data class NoGoodWindow(val reason: String) : MowAdvice
    data object OffSeason : MowAdvice
}

/** A run of consecutive snowy forecast slots. */
data class SnowEvent(
    val start: Instant,
    val end: Instant,
    val totalCm: Double,
)

data class SnowOutlook(
    val events: List<SnowEvent>,
    val next24hCm: Double,
    val next48hCm: Double,
    /** True when the next 24 hours exceed the user's alert threshold. */
    val alert: Boolean,
    /** Best time to clear after the next significant snowfall, if any. */
    val clearAt: Instant?,
    /** For heavy storms, an extra clearing pass partway through. */
    val midStormClearAt: Instant?,
) {
    val nextEvent: SnowEvent? get() = events.firstOrNull()
    val hasSnow: Boolean get() = events.isNotEmpty()
}

data class DailySummary(
    val date: LocalDate,
    val minTempC: Double,
    val maxTempC: Double,
    val rainMm: Double,
    val snowCm: Double,
    val maxPrecipProbability: Double,
    val condition: String,
)

package com.azeemzsyed.yardready.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Turns a raw forecast into yard-work advice. Pure Kotlin with no Android
 * dependencies, so all of the decision logic is covered by fast unit tests.
 */
class RecommendationEngine(private val config: Config = Config()) {

    data class Config(
        // Mowing
        val mowMinTempC: Double = 8.0,
        val mowMaxTempC: Double = 30.0,
        val mowMaxPrecipProbability: Double = 0.3,
        val mowMaxWindMs: Double = 10.0,
        /** Grass needs this many dry hours before a slot to be cut cleanly. */
        val dryHoursBeforeMow: Long = 12,
        val maxRainBeforeMowMm: Double = 1.0,
        val mowStartHour: Int = 8,
        val mowEndHour: Int = 20,
        val minDaysBetweenMows: Long = 4,
        // Snow
        /** cm of snow per mm of water equivalent (classic 10:1 ratio => 1 mm -> 1 cm). */
        val snowCmPerMm: Double = 1.0,
        val alertThresholdCm: Double = 5.0,
        val clearingThresholdCm: Double = 2.0,
        val heavySnowCm: Double = 10.0,
        val clearStartHour: Int = 6,
        val clearEndHour: Int = 21,
    )

    // ---------------------------------------------------------------- mowing

    fun mowAdvice(forecast: Forecast, now: Instant, lastMowed: Instant?): MowAdvice {
        val zone = forecast.zone
        val future = forecast.slots.filter { !it.time.isBefore(now) }
        if (future.isEmpty()) return MowAdvice.NoGoodWindow("No forecast data available")

        if (lastMowed != null) {
            val nextDue = lastMowed.plus(Duration.ofDays(config.minDaysBetweenMows))
            if (now.isBefore(nextDue)) {
                // (LocalDate.ofInstant needs API 34, so go through OffsetDateTime)
                return MowAdvice.NotNeededYet(lastMowed, nextDue.atOffset(zone).toLocalDate())
            }
        }

        if (future.all { it.tempC < config.mowMinTempC }) return MowAdvice.OffSeason

        val candidates = future.filter { slot ->
            val hour = slot.time.atOffset(zone).hour
            hour in config.mowStartHour until config.mowEndHour &&
                slot.rainMm < 0.1 && slot.snowMm <= 0.0 &&
                slot.precipProbability <= config.mowMaxPrecipProbability &&
                slot.tempC in config.mowMinTempC..config.mowMaxTempC &&
                slot.windMs <= config.mowMaxWindMs &&
                rainBefore(forecast.slots, slot.time) <= config.maxRainBeforeMowMm
        }

        val best = candidates.maxByOrNull { mowScore(it, now, zone) }
            ?: return MowAdvice.NoGoodWindow(noWindowReason(future))

        return MowAdvice.MowAt(best, reasonsFor(best, forecast))
    }

    /** Total rain (mm) in the dry-hours window before [time]. */
    private fun rainBefore(slots: List<ForecastSlot>, time: Instant): Double {
        val from = time.minus(Duration.ofHours(config.dryHoursBeforeMow))
        return slots.filter { it.time >= from && it.time < time }.sumOf { it.rainMm }
    }

    private fun mowScore(slot: ForecastSlot, now: Instant, zone: java.time.ZoneOffset): Double {
        val hoursAway = Duration.between(now, slot.time).toHours().toDouble()
        val hour = slot.time.atOffset(zone).hour
        val middayBonus = if (hour in 11..17) 5.0 else 0.0 // dew has dried off
        return 100.0 -
            abs(slot.tempC - 21.0) * 2.0 -
            slot.precipProbability * 50.0 -
            slot.windMs * 2.0 -
            hoursAway * 0.15 +
            middayBonus
    }

    private fun reasonsFor(slot: ForecastSlot, forecast: Forecast): List<String> = buildList {
        add("${slot.tempC.toInt()}°C and ${slot.description.ifBlank { slot.condition.lowercase() }}")
        add("${(slot.precipProbability * 100).toInt()}% chance of rain")
        if (rainBefore(forecast.slots, slot.time) == 0.0) add("Grass will be dry: no rain in the ${config.dryHoursBeforeMow} hours before")
        if (slot.windMs < 5.0) add("Light wind")
    }

    private fun noWindowReason(future: List<ForecastSlot>): String = when {
        future.count { it.rainMm >= 0.1 || it.precipProbability > config.mowMaxPrecipProbability } > future.size / 2 ->
            "Rain or wet grass expected for most of the next 5 days"
        future.all { it.tempC > config.mowMaxTempC || it.tempC < config.mowMinTempC } ->
            "Temperatures are outside the comfortable mowing range"
        else -> "No dry, calm daytime window in the next 5 days"
    }

    // ------------------------------------------------------------------ snow

    fun snowOutlook(forecast: Forecast, now: Instant, alertThresholdCm: Double = config.alertThresholdCm): SnowOutlook {
        val future = forecast.slots.filter { !it.time.isBefore(now) }.sortedBy { it.time }
        val events = snowEvents(future)

        val next24 = future.filter { it.time < now.plus(Duration.ofHours(24)) }.sumOf { cm(it) }
        val next48 = future.filter { it.time < now.plus(Duration.ofHours(48)) }.sumOf { cm(it) }

        val significant = events.firstOrNull { it.totalCm >= config.clearingThresholdCm }
        val clearAt = significant?.let { clearingTime(future, it, forecast) }
        val midStorm = significant
            ?.takeIf { it.totalCm >= config.heavySnowCm }
            ?.let { midStormClearing(future, it, forecast) }

        return SnowOutlook(
            events = events,
            next24hCm = round1(next24),
            next48hCm = round1(next48),
            alert = next24 >= alertThresholdCm,
            clearAt = clearAt,
            midStormClearAt = midStorm,
        )
    }

    private fun cm(slot: ForecastSlot) = slot.snowMm * config.snowCmPerMm

    /** Groups snowy slots into events, allowing a single dry 3-hour gap inside a storm. */
    internal fun snowEvents(slots: List<ForecastSlot>): List<SnowEvent> {
        val events = mutableListOf<SnowEvent>()
        var start: ForecastSlot? = null
        var last: ForecastSlot? = null
        var total = 0.0
        var dryGap = 0

        fun close() {
            if (start != null && last != null) {
                events += SnowEvent(start!!.time, last!!.time.plus(Duration.ofHours(3)), round1(total))
            }
            start = null; last = null; total = 0.0; dryGap = 0
        }

        for (slot in slots) {
            if (slot.snowMm > 0.0) {
                if (start == null) start = slot
                last = slot
                total += cm(slot)
                dryGap = 0
            } else if (start != null) {
                dryGap++
                if (dryGap > 1) close()
            }
        }
        close()
        return events
    }

    /** First daytime slot after the event ends (when it is safe and useful to clear). */
    private fun clearingTime(slots: List<ForecastSlot>, event: SnowEvent, forecast: Forecast): Instant {
        val afterStorm = slots.firstOrNull { slot ->
            slot.time >= event.end && slot.snowMm <= 0.0 && isClearingHour(slot.time, forecast)
        }
        return afterStorm?.time ?: event.end
    }

    private fun midStormClearing(slots: List<ForecastSlot>, event: SnowEvent, forecast: Forecast): Instant? {
        val midpoint = event.start.plus(Duration.between(event.start, event.end).dividedBy(2))
        return slots
            .filter { it.time >= event.start && it.time < event.end && isClearingHour(it.time, forecast) }
            .minByOrNull { abs(Duration.between(midpoint, it.time).toMinutes()) }
            ?.time
    }

    private fun isClearingHour(time: Instant, forecast: Forecast): Boolean =
        time.atOffset(forecast.zone).hour in config.clearStartHour until config.clearEndHour

    // ----------------------------------------------------------- daily view

    fun dailySummaries(forecast: Forecast, days: Int = 5): List<DailySummary> =
        forecast.slots
            .groupBy { it.time.atOffset(forecast.zone).toLocalDate() }
            .toSortedMap()
            .map { (date, slots) ->
                DailySummary(
                    date = date,
                    minTempC = slots.minOf { it.tempC },
                    maxTempC = slots.maxOf { it.tempC },
                    rainMm = round1(slots.sumOf { it.rainMm }),
                    snowCm = round1(slots.sumOf { cm(it) }),
                    maxPrecipProbability = slots.maxOf { it.precipProbability },
                    condition = dominantCondition(slots),
                )
            }
            .take(days)

    /** Most significant weather of the day: snow > rain > clouds > clear. */
    private fun dominantCondition(slots: List<ForecastSlot>): String {
        val severity = listOf("Thunderstorm", "Snow", "Rain", "Drizzle", "Clouds", "Clear")
        return slots.map { it.condition }
            .minByOrNull { c -> severity.indexOf(c).let { if (it < 0) severity.size else it } }
            ?: "Clear"
    }

    private fun round1(x: Double) = Math.round(x * 10.0) / 10.0
}

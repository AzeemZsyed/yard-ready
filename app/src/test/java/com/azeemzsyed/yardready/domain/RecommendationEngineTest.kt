package com.azeemzsyed.yardready.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RecommendationEngineTest {

    private val engine = RecommendationEngine()
    private val zone = ZoneOffset.ofHours(-5) // Toronto in winter
    /** Monday 10 June 2024, 05:00 local time. */
    private val now: Instant = Instant.parse("2024-06-10T10:00:00Z")

    /** Builds 40 three-hour slots (5 days) starting at [now], overridable per index. */
    private fun forecast(
        tempC: Double = 20.0,
        override: (index: Int, slot: ForecastSlot) -> ForecastSlot = { _, s -> s },
    ) = Forecast(
        locationName = "Mississauga",
        zone = zone,
        slots = (0 until 40).map { i ->
            override(
                i,
                ForecastSlot(
                    time = now.plus(Duration.ofHours(3L * i)),
                    tempC = tempC,
                    precipProbability = 0.0,
                    rainMm = 0.0,
                    snowMm = 0.0,
                    windMs = 3.0,
                    condition = "Clear",
                    description = "clear sky",
                ),
            )
        },
    )

    private fun localHour(t: Instant) = t.atOffset(zone).hour

    // ---------------------------------------------------------------- mowing

    @Test
    fun `recommends a daytime slot on a dry warm forecast`() {
        val advice = engine.mowAdvice(forecast(), now, lastMowed = null)
        assertTrue(advice is MowAdvice.MowAt)
        val hour = localHour((advice as MowAdvice.MowAt).slot.time)
        assertTrue("hour was $hour", hour in 8 until 20)
    }

    @Test
    fun `prefers midday so the dew has dried`() {
        val advice = engine.mowAdvice(forecast(), now, null) as MowAdvice.MowAt
        assertTrue(localHour(advice.slot.time) in 11..17)
    }

    @Test
    fun `never recommends a slot while it is raining`() {
        // Rain all of the first two days
        val f = forecast { i, s -> if (i < 16) s.copy(rainMm = 2.0, precipProbability = 0.9, condition = "Rain") else s }
        val advice = engine.mowAdvice(f, now, null) as MowAdvice.MowAt
        assertTrue(advice.slot.rainMm < 0.1)
        assertTrue(advice.slot.time >= f.slots[16].time)
    }

    @Test
    fun `waits for grass to dry after rain`() {
        // Rain ends at slot 16; the next 12 hours (4 slots) are too wet
        val f = forecast { i, s -> if (i < 16) s.copy(rainMm = 3.0, precipProbability = 0.9) else s }
        val advice = engine.mowAdvice(f, now, null) as MowAdvice.MowAt
        val hoursAfterRain = Duration.between(f.slots[16].time, advice.slot.time).toHours()
        assertTrue("only $hoursAfterRain h after rain", hoursAfterRain >= 12)
    }

    @Test
    fun `not needed if mowed recently`() {
        val lastMowed = now.minus(Duration.ofDays(2))
        val advice = engine.mowAdvice(forecast(), now, lastMowed)
        assertTrue(advice is MowAdvice.NotNeededYet)
        assertEquals(LocalDate.of(2024, 6, 12), (advice as MowAdvice.NotNeededYet).nextDue)
    }

    @Test
    fun `due again after the minimum interval`() {
        val advice = engine.mowAdvice(forecast(), now, now.minus(Duration.ofDays(5)))
        assertTrue(advice is MowAdvice.MowAt)
    }

    @Test
    fun `off season when it is cold all week`() {
        assertEquals(MowAdvice.OffSeason, engine.mowAdvice(forecast(tempC = -4.0), now, null))
    }

    @Test
    fun `no window when it rains all week`() {
        val f = forecast { _, s -> s.copy(rainMm = 1.5, precipProbability = 0.8, condition = "Rain") }
        val advice = engine.mowAdvice(f, now, null)
        assertTrue(advice is MowAdvice.NoGoodWindow)
        assertTrue((advice as MowAdvice.NoGoodWindow).reason.contains("Rain"))
    }

    @Test
    fun `avoids windy slots`() {
        val f = forecast { i, s -> if (i < 20) s.copy(windMs = 14.0) else s }
        val advice = engine.mowAdvice(f, now, null) as MowAdvice.MowAt
        assertTrue(advice.slot.windMs <= 10.0)
    }

    // ------------------------------------------------------------------ snow

    private fun snowyForecast(snowSlots: IntRange, mmPerSlot: Double) =
        forecast(tempC = -3.0) { i, s -> if (i in snowSlots) s.copy(snowMm = mmPerSlot, precipProbability = 0.9, condition = "Snow") else s }

    @Test
    fun `no snow means no events and no alert`() {
        val outlook = engine.snowOutlook(forecast(tempC = -2.0), now)
        assertFalse(outlook.hasSnow)
        assertFalse(outlook.alert)
        assertNull(outlook.clearAt)
    }

    @Test
    fun `alerts when 24h accumulation passes the threshold`() {
        val outlook = engine.snowOutlook(snowyForecast(2..5, 1.5), now) // 4 slots x 1.5 cm = 6 cm
        assertTrue(outlook.alert)
        assertEquals(6.0, outlook.next24hCm, 0.01)
        assertEquals(1, outlook.events.size)
        assertEquals(6.0, outlook.events[0].totalCm, 0.01)
    }

    @Test
    fun `respects a custom alert threshold`() {
        val outlook = engine.snowOutlook(snowyForecast(2..5, 1.5), now, alertThresholdCm = 10.0)
        assertFalse(outlook.alert)
    }

    @Test
    fun `light flurries do not trigger an alert`() {
        val outlook = engine.snowOutlook(snowyForecast(2..2, 0.4), now)
        assertTrue(outlook.hasSnow)
        assertFalse(outlook.alert)
        assertNull("too little snow to clear", outlook.clearAt)
    }

    @Test
    fun `snow later in the week counts toward 48h but not 24h`() {
        val outlook = engine.snowOutlook(snowyForecast(10..12, 2.0), now) // starts 30 h from now
        assertEquals(0.0, outlook.next24hCm, 0.01)
        assertEquals(6.0, outlook.next48hCm, 0.01)
        assertFalse(outlook.alert)
    }

    @Test
    fun `one dry slot inside a storm keeps it a single event`() {
        val f = forecast(tempC = -3.0) { i, s -> if (i in listOf(2, 3, 5, 6)) s.copy(snowMm = 1.0) else s }
        assertEquals(1, engine.snowOutlook(f, now).events.size)
    }

    @Test
    fun `two dry slots split storms into separate events`() {
        val f = forecast(tempC = -3.0) { i, s -> if (i in listOf(2, 3, 6, 7)) s.copy(snowMm = 1.0) else s }
        assertEquals(2, engine.snowOutlook(f, now).events.size)
    }

    @Test
    fun `clearing time is after the storm and during the day`() {
        val f = snowyForecast(2..5, 1.5)
        val outlook = engine.snowOutlook(f, now)
        val clearAt = assertNotNullAndGet(outlook.clearAt)
        assertTrue(clearAt >= outlook.events[0].end)
        assertTrue(localHour(clearAt) in 6 until 21)
    }

    @Test
    fun `heavy storms get a mid-storm clearing suggestion`() {
        val outlook = engine.snowOutlook(snowyForecast(4..11, 2.0), now) // 16 cm over 24 h
        assertNotNull(outlook.midStormClearAt)
        val mid = outlook.midStormClearAt!!
        assertTrue(mid >= outlook.events[0].start && mid < outlook.events[0].end)
    }

    @Test
    fun `moderate storms do not need a mid-storm pass`() {
        assertNull(engine.snowOutlook(snowyForecast(2..5, 1.5), now).midStormClearAt)
    }

    // ------------------------------------------------------------ daily view

    @Test
    fun `daily summaries group slots by local date`() {
        val f = forecast { i, s -> s.copy(tempC = 10.0 + i % 8, rainMm = if (i == 3) 2.5 else 0.0) }
        val days = engine.dailySummaries(f)
        assertEquals(5, days.size)
        assertEquals(LocalDate.of(2024, 6, 10), days[0].date)
        assertEquals(2.5, days[0].rainMm, 0.01)
        assertTrue(days.all { it.maxTempC >= it.minTempC })
    }

    @Test
    fun `snow is the dominant condition when it snows at all`() {
        val days = engine.dailySummaries(snowyForecast(1..1, 1.0))
        assertEquals("Snow", days[0].condition)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}

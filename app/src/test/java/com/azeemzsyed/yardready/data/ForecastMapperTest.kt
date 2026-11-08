package com.azeemzsyed.yardready.data

import com.azeemzsyed.yardready.data.remote.CityDto
import com.azeemzsyed.yardready.data.remote.ForecastItemDto
import com.azeemzsyed.yardready.data.remote.ForecastResponse
import com.azeemzsyed.yardready.data.remote.MainDto
import com.azeemzsyed.yardready.data.remote.PrecipDto
import com.azeemzsyed.yardready.data.remote.WeatherDto
import com.azeemzsyed.yardready.data.remote.WindDto
import com.azeemzsyed.yardready.data.remote.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ForecastMapperTest {

    private fun item(dt: Long, rain: Double? = null, snow: Double? = null, pop: Double? = 0.2) = ForecastItemDto(
        dt = dt,
        main = MainDto(temp = -1.5),
        weather = listOf(WeatherDto(main = "Snow", description = "light snow")),
        wind = WindDto(speed = 4.2),
        pop = pop,
        rain = rain?.let { PrecipDto(it) },
        snow = snow?.let { PrecipDto(it) },
    )

    @Test
    fun `maps fields and fills missing precipitation with zero`() {
        val response = ForecastResponse(
            list = listOf(item(1_700_000_000, snow = 1.25)),
            city = CityDto(name = "Mississauga", country = "CA", timezone = -18000),
        )
        val forecast = response.toDomain(fallbackName = "fallback")
        val slot = forecast.slots.single()

        assertEquals("Mississauga", forecast.locationName)
        assertEquals(ZoneOffset.ofHours(-5), forecast.zone)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), slot.time)
        assertEquals(1.25, slot.snowMm, 0.0)
        assertEquals(0.0, slot.rainMm, 0.0)
        assertEquals(-1.5, slot.tempC, 0.0)
        assertEquals(4.2, slot.windMs, 0.0)
        assertEquals("Snow", slot.condition)
    }

    @Test
    fun `sorts slots by time and clamps probability`() {
        val response = ForecastResponse(
            list = listOf(item(200, pop = 1.4), item(100, pop = -0.1)),
            city = null,
        )
        val forecast = response.toDomain(fallbackName = "Guelph")
        assertEquals("Guelph", forecast.locationName)
        assertEquals(listOf(100L, 200L), forecast.slots.map { it.time.epochSecond })
        assertEquals(0.0, forecast.slots[0].precipProbability, 0.0)
        assertEquals(1.0, forecast.slots[1].precipProbability, 0.0)
    }

    @Test
    fun `handles an empty response`() {
        val forecast = ForecastResponse(list = null, city = null).toDomain("Nowhere")
        assertEquals(0, forecast.slots.size)
        assertEquals(ZoneOffset.UTC, forecast.zone)
    }
}

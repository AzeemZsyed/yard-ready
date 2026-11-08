package com.azeemzsyed.yardready.data

import com.azeemzsyed.yardready.data.remote.OpenWeatherApi
import com.azeemzsyed.yardready.data.remote.toDomain
import com.azeemzsyed.yardready.domain.Forecast

data class CityResult(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val label: String get() = listOfNotNull(name, region, country).joinToString(", ")
}

/** Abstraction so UI tests can swap in a fake forecast. */
interface WeatherRepository {
    suspend fun forecast(latitude: Double, longitude: Double, name: String): Forecast
    suspend fun searchCities(query: String): List<CityResult>
}

class MissingApiKeyException : IllegalStateException(
    "Add OPENWEATHER_API_KEY to local.properties (see README)"
)

class OpenWeatherRepository(
    private val api: OpenWeatherApi,
    private val apiKey: String,
) : WeatherRepository {

    override suspend fun forecast(latitude: Double, longitude: Double, name: String): Forecast {
        requireKey()
        return api.forecast(latitude, longitude, apiKey).toDomain(fallbackName = name)
    }

    override suspend fun searchCities(query: String): List<CityResult> {
        requireKey()
        if (query.isBlank()) return emptyList()
        return api.geocode(query.trim(), apiKey).mapNotNull { dto ->
            val lat = dto.lat ?: return@mapNotNull null
            val lon = dto.lon ?: return@mapNotNull null
            CityResult(dto.name ?: query, dto.state, dto.country, lat, lon)
        }
    }

    private fun requireKey() {
        if (apiKey.isBlank()) throw MissingApiKeyException()
    }
}

package com.azeemzsyed.yardready.data.remote

import com.azeemzsyed.yardready.domain.Forecast
import com.azeemzsyed.yardready.domain.ForecastSlot
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import java.time.ZoneOffset

/** OpenWeatherMap endpoints available on the free plan. */
interface OpenWeatherApi {

    /** 5 day forecast in 3-hour steps. https://openweathermap.org/forecast5 */
    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
    ): ForecastResponse

    /** City name -> coordinates. https://openweathermap.org/api/geocoding-api */
    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") query: String,
        @Query("appid") apiKey: String,
        @Query("limit") limit: Int = 5,
    ): List<GeoResultDto>

    companion object {
        const val BASE_URL = "https://api.openweathermap.org/"
    }
}

// ---- DTOs (fields are nullable because the API omits empty blocks) ---------

data class ForecastResponse(
    val list: List<ForecastItemDto>?,
    val city: CityDto?,
)

data class ForecastItemDto(
    val dt: Long,
    val main: MainDto?,
    val weather: List<WeatherDto>?,
    val wind: WindDto?,
    val pop: Double?,
    val rain: PrecipDto?,
    val snow: PrecipDto?,
)

data class MainDto(val temp: Double?)
data class WeatherDto(val main: String?, val description: String?)
data class WindDto(val speed: Double?)
data class PrecipDto(@SerializedName("3h") val threeHour: Double?)
data class CityDto(val name: String?, val country: String?, val timezone: Int?)

data class GeoResultDto(
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,
    val state: String?,
)

// ---- Mapping to domain models ---------------------------------------------------

fun ForecastResponse.toDomain(fallbackName: String): Forecast = Forecast(
    locationName = city?.name ?: fallbackName,
    zone = ZoneOffset.ofTotalSeconds(city?.timezone ?: 0),
    slots = list.orEmpty().map { it.toDomain() }.sortedBy { it.time },
)

fun ForecastItemDto.toDomain(): ForecastSlot {
    val w = weather?.firstOrNull()
    return ForecastSlot(
        time = Instant.ofEpochSecond(dt),
        tempC = main?.temp ?: 0.0,
        precipProbability = (pop ?: 0.0).coerceIn(0.0, 1.0),
        rainMm = rain?.threeHour ?: 0.0,
        snowMm = snow?.threeHour ?: 0.0,
        windMs = wind?.speed ?: 0.0,
        condition = w?.main ?: "Clear",
        description = w?.description ?: "",
    )
}

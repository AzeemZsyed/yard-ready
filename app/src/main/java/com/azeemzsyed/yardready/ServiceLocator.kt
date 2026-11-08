package com.azeemzsyed.yardready

import android.content.Context
import com.azeemzsyed.yardready.data.MaintenanceRepository
import com.azeemzsyed.yardready.data.OpenWeatherRepository
import com.azeemzsyed.yardready.data.SettingsStore
import com.azeemzsyed.yardready.data.WeatherRepository
import com.azeemzsyed.yardready.data.local.AppDatabase
import com.azeemzsyed.yardready.data.remote.OpenWeatherApi
import com.azeemzsyed.yardready.data.sync.CloudSync
import com.azeemzsyed.yardready.data.sync.FirebaseCloudSync
import com.azeemzsyed.yardready.data.sync.NoOpCloudSync
import com.azeemzsyed.yardready.domain.RecommendationEngine
import com.google.firebase.FirebaseApp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Minimal manual dependency injection. Tests replace [weatherRepository],
 * [database] and [clock] before launching an activity.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Volatile var clock: Clock = Clock.systemDefaultZone()

    val engine = RecommendationEngine()

    @Volatile private var _database: AppDatabase? = null
    var database: AppDatabase
        get() = _database ?: synchronized(this) {
            _database ?: AppDatabase.build(appContext).also { _database = it }
        }
        set(value) {
            _database = value
            _maintenance = null
        }

    @Volatile private var _weather: WeatherRepository? = null
    var weatherRepository: WeatherRepository
        get() = _weather ?: synchronized(this) {
            _weather ?: OpenWeatherRepository(createApi(), BuildConfig.OPENWEATHER_API_KEY).also { _weather = it }
        }
        set(value) { _weather = value }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val cloudSync: CloudSync by lazy {
        // Firebase is optional: it is only initialised when google-services.json exists.
        if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseCloudSync() else NoOpCloudSync
    }

    @Volatile private var _maintenance: MaintenanceRepository? = null
    val maintenanceRepository: MaintenanceRepository
        get() = _maintenance ?: synchronized(this) {
            _maintenance ?: MaintenanceRepository(database.maintenanceDao(), cloudSync).also { _maintenance = it }
        }

    private fun createApi(): OpenWeatherApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(OpenWeatherApi.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }
}

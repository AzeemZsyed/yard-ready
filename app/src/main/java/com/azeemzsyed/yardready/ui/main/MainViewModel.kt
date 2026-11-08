package com.azeemzsyed.yardready.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azeemzsyed.yardready.ServiceLocator
import com.azeemzsyed.yardready.data.MaintenanceRepository
import com.azeemzsyed.yardready.data.MissingApiKeyException
import com.azeemzsyed.yardready.data.SettingsStore
import com.azeemzsyed.yardready.data.WeatherRepository
import com.azeemzsyed.yardready.data.local.LocationDao
import com.azeemzsyed.yardready.data.local.SavedLocation
import com.azeemzsyed.yardready.data.local.TaskType
import com.azeemzsyed.yardready.domain.DailySummary
import com.azeemzsyed.yardready.domain.Forecast
import com.azeemzsyed.yardready.domain.MowAdvice
import com.azeemzsyed.yardready.domain.RecommendationEngine
import com.azeemzsyed.yardready.domain.SnowOutlook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Instant

data class DashboardState(
    val loading: Boolean = false,
    val noLocation: Boolean = false,
    val location: SavedLocation? = null,
    val forecast: Forecast? = null,
    val mow: MowAdvice? = null,
    val snow: SnowOutlook? = null,
    val days: List<DailySummary> = emptyList(),
    val lastMowed: Long? = null,
    val lastCleared: Long? = null,
    val updatedAt: Instant? = null,
    val error: String? = null,
)

class MainViewModel(
    private val locationDao: LocationDao,
    private val maintenance: MaintenanceRepository,
    private val weather: WeatherRepository,
    private val settings: SettingsStore,
    private val engine: RecommendationEngine,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState(loading = true))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    /** One-off events for the UI (e.g. a snackbar after logging a task). */
    private val _events = MutableSharedFlow<TaskType>(extraBufferCapacity = 1)
    val taskLogged: SharedFlow<TaskType> = _events.asSharedFlow()

    private var currentLocationId: Long? = null
    private var logsJob: Job? = null

    init {
        viewModelScope.launch {
            locationDao.observeSelected().collect { location ->
                if (location == null) {
                    currentLocationId = null
                    _state.value = DashboardState(noLocation = true)
                    return@collect
                }
                val changed = location.id != currentLocationId
                currentLocationId = location.id
                _state.update { it.copy(location = location, noLocation = false) }
                if (changed) {
                    observeLogs(location.id)
                    refresh()
                }
            }
        }
        // Re-evaluate the snow alert when the user changes the threshold.
        viewModelScope.launch { settings.snowThresholdCm.collect { recompute() } }
    }

    private fun observeLogs(locationId: Long) {
        logsJob?.cancel()
        logsJob = viewModelScope.launch {
            combine(
                maintenance.lastCompleted(locationId, TaskType.MOW),
                maintenance.lastCompleted(locationId, TaskType.CLEAR_SNOW),
            ) { mowed, cleared -> mowed to cleared }
                .collect { (mowed, cleared) ->
                    _state.update { it.copy(lastMowed = mowed, lastCleared = cleared) }
                    recompute()
                }
        }
    }

    fun refresh() {
        val location = _state.value.location ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val forecast = weather.forecast(location.latitude, location.longitude, location.name)
                _state.update { it.copy(forecast = forecast, loading = false, updatedAt = clock.instant()) }
                recompute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = describe(e)) }
            }
        }
    }

    private fun recompute() {
        val forecast = _state.value.forecast ?: return
        val now = clock.instant()
        _state.update {
            it.copy(
                mow = engine.mowAdvice(forecast, now, it.lastMowed?.let(Instant::ofEpochMilli)),
                snow = engine.snowOutlook(forecast, now, settings.snowThresholdCm.value.toDouble()),
                days = engine.dailySummaries(forecast),
            )
        }
    }

    fun markDone(type: TaskType) {
        val location = _state.value.location ?: return
        viewModelScope.launch {
            maintenance.log(location.id, type, clock.instant())
            _events.tryEmit(type)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is MissingApiKeyException -> e.message!!
        is HttpException -> if (e.code() == 401) "OpenWeatherMap rejected the API key" else "Weather service error (${e.code()})"
        is IOException -> "No internet connection. Pull down to try again."
        else -> e.message ?: "Could not load the forecast"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    locationDao = ServiceLocator.database.locationDao(),
                    maintenance = ServiceLocator.maintenanceRepository,
                    weather = ServiceLocator.weatherRepository,
                    settings = ServiceLocator.settings,
                    engine = ServiceLocator.engine,
                    clock = ServiceLocator.clock,
                )
            }
        }
    }
}

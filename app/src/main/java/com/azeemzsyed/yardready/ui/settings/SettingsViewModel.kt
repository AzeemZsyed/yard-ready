package com.azeemzsyed.yardready.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azeemzsyed.yardready.ServiceLocator
import com.azeemzsyed.yardready.data.CityResult
import com.azeemzsyed.yardready.data.SettingsStore
import com.azeemzsyed.yardready.data.WeatherRepository
import com.azeemzsyed.yardready.data.local.LocationDao
import com.azeemzsyed.yardready.data.local.SavedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val searching: Boolean = false,
    val results: List<CityResult> = emptyList(),
    val error: String? = null,
)

class SettingsViewModel(
    private val locationDao: LocationDao,
    private val weather: WeatherRepository,
    val settings: SettingsStore,
) : ViewModel() {

    val selected: StateFlow<SavedLocation?> =
        locationDao.observeSelected().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _search.value = SearchState(searching = true)
            try {
                val results = weather.searchCities(query)
                _search.value = SearchState(
                    results = results,
                    error = if (results.isEmpty()) "No places found for \"$query\"" else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.value = SearchState(error = e.message ?: "Search failed")
            }
        }
    }

    fun choose(city: CityResult, onSaved: () -> Unit) {
        viewModelScope.launch {
            locationDao.saveAndSelect(
                SavedLocation(
                    name = city.name,
                    region = city.region,
                    country = city.country,
                    latitude = city.latitude,
                    longitude = city.longitude,
                )
            )
            _search.update { SearchState() }
            onSaved()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(ServiceLocator.database.locationDao(), ServiceLocator.weatherRepository, ServiceLocator.settings)
            }
        }
    }
}

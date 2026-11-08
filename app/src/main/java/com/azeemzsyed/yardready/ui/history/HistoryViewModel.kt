package com.azeemzsyed.yardready.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azeemzsyed.yardready.ServiceLocator
import com.azeemzsyed.yardready.data.MaintenanceRepository
import com.azeemzsyed.yardready.data.local.LocationDao
import com.azeemzsyed.yardready.data.local.MaintenanceLog
import com.azeemzsyed.yardready.data.local.TaskStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

data class HistoryState(
    val logs: List<MaintenanceLog> = emptyList(),
    val stats: List<TaskStats> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    locationDao: LocationDao,
    private val maintenance: MaintenanceRepository,
    clock: Clock,
) : ViewModel() {

    private val seasonStart = clock.instant().minus(Duration.ofDays(365))

    val state: StateFlow<HistoryState> = locationDao.observeSelected()
        .flatMapLatest { location ->
            if (location == null) flowOf(HistoryState())
            else combine(
                maintenance.history(location.id),
                maintenance.stats(location.id, seasonStart),
            ) { logs, stats -> HistoryState(logs, stats) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun delete(log: MaintenanceLog) {
        viewModelScope.launch { maintenance.delete(log) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HistoryViewModel(ServiceLocator.database.locationDao(), ServiceLocator.maintenanceRepository, ServiceLocator.clock)
            }
        }
    }
}

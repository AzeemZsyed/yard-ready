package com.azeemzsyed.yardready.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.azeemzsyed.yardready.R
import com.azeemzsyed.yardready.data.local.TaskType
import com.azeemzsyed.yardready.databinding.ActivityMainBinding
import com.azeemzsyed.yardready.domain.Forecast
import com.azeemzsyed.yardready.domain.MowAdvice
import com.azeemzsyed.yardready.domain.SnowOutlook
import com.azeemzsyed.yardready.ui.Format
import com.azeemzsyed.yardready.ui.history.HistoryActivity
import com.azeemzsyed.yardready.ui.settings.SettingsActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val dailyAdapter = DailyForecastAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> viewModel.refresh()
                R.id.action_history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.action_settings -> openSettings()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.retryButton.setOnClickListener { viewModel.refresh() }
        binding.setLocationButton.setOnClickListener { openSettings() }
        binding.markMowedButton.setOnClickListener { viewModel.markDone(TaskType.MOW) }
        binding.markClearedButton.setOnClickListener { viewModel.markDone(TaskType.CLEAR_SNOW) }
        binding.dailyList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.dailyList.adapter = dailyAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.taskLogged.collect { type ->
                        val msg = if (type == TaskType.MOW) R.string.logged_mow else R.string.logged_snow
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun render(state: DashboardState) = with(binding) {
        swipeRefresh.isRefreshing = state.loading && state.forecast != null
        initialProgress.visibility = if (state.loading && state.forecast == null && !state.noLocation) View.VISIBLE else View.GONE

        emptyState.visibility = if (state.noLocation) View.VISIBLE else View.GONE
        locationName.text = state.forecast?.locationName ?: state.location?.name ?: getString(R.string.app_name)
        updatedText.text = state.updatedAt?.let { getString(R.string.updated_at, Format.dateTime(it.toEpochMilli())) } ?: ""

        errorCard.visibility = if (state.error != null) View.VISIBLE else View.GONE
        errorText.text = state.error

        val forecast = state.forecast
        content.visibility = if (forecast != null && !state.noLocation) View.VISIBLE else View.GONE
        if (forecast == null) return@with

        renderMow(state.mow, forecast, state.lastMowed)
        renderSnow(state.snow, forecast, state.lastCleared)
        dailyAdapter.submitList(state.days)
    }

    private fun renderMow(advice: MowAdvice?, forecast: Forecast, lastMowed: Long?) = with(binding) {
        when (advice) {
            is MowAdvice.MowAt -> {
                mowHeadline.text = getString(R.string.mow_best_time, Format.dayTime(advice.slot.time, forecast.zone))
                mowDetails.text = advice.reasons.joinToString("\n") { "• $it" }
            }
            is MowAdvice.NotNeededYet -> {
                mowHeadline.text = getString(R.string.mow_not_needed)
                mowDetails.text = getString(R.string.mow_next_due, Format.dayDate(advice.nextDue))
            }
            is MowAdvice.NoGoodWindow -> {
                mowHeadline.text = getString(R.string.mow_no_window)
                mowDetails.text = advice.reason
            }
            MowAdvice.OffSeason -> {
                mowHeadline.text = getString(R.string.mow_off_season)
                mowDetails.text = getString(R.string.mow_off_season_details)
            }
            null -> Unit
        }
        mowLast.text = lastMowed?.let { getString(R.string.last_mowed, Format.dateTime(it)) } ?: getString(R.string.never_mowed)
    }

    private fun renderSnow(outlook: SnowOutlook?, forecast: Forecast, lastCleared: Long?) = with(binding) {
        if (outlook == null) return@with
        snowAlertBanner.visibility = if (outlook.alert) View.VISIBLE else View.GONE
        snowAlertText.text = getString(R.string.snow_alert_banner, Format.cm(outlook.next24hCm))

        val event = outlook.nextEvent
        if (event == null) {
            snowHeadline.text = getString(R.string.snow_none)
            snowDetails.text = getString(R.string.snow_none_details)
        } else {
            snowHeadline.text = getString(R.string.snow_expected, Format.cm(event.totalCm), Format.dayTime(event.start, forecast.zone))
            val lines = mutableListOf(getString(R.string.snow_totals, Format.cm(outlook.next24hCm), Format.cm(outlook.next48hCm)))
            val clearAt = outlook.clearAt
            if (clearAt != null) {
                lines.add(getString(R.string.snow_clear_at, Format.dayTime(clearAt, forecast.zone)))
            } else {
                lines.add(getString(R.string.snow_light))
            }
            outlook.midStormClearAt?.let { lines.add(getString(R.string.snow_mid_storm, Format.dayTime(it, forecast.zone))) }
            snowDetails.text = lines.joinToString("\n") { "• $it" }
        }
        snowLast.text = lastCleared?.let { getString(R.string.last_cleared, Format.dateTime(it)) } ?: getString(R.string.never_cleared)
    }
}

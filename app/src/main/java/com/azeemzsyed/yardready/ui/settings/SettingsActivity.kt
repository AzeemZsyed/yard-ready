package com.azeemzsyed.yardready.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azeemzsyed.yardready.R
import com.azeemzsyed.yardready.data.CityResult
import com.azeemzsyed.yardready.databinding.ActivitySettingsBinding
import com.azeemzsyed.yardready.databinding.ItemCityResultBinding
import com.azeemzsyed.yardready.notifications.SnowAlertNotifier
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.settings.setAlertsEnabled(granted)
            binding.alertsSwitch.isChecked = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = CityAdapter { city -> viewModel.choose(city) { finish() } }
        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = adapter

        binding.searchButton.setOnClickListener { runSearch() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }

        // Snow alert settings
        val settings = viewModel.settings
        // Slider crashes on values outside its range/step, so clamp and round first
        val threshold = settings.snowThresholdCm.value.coerceIn(1f, 20f).roundToInt().toFloat()
        binding.thresholdSlider.value = threshold
        binding.thresholdValue.text = getString(R.string.threshold_value, threshold.toInt())
        binding.thresholdSlider.addOnChangeListener { _, value, _ ->
            settings.setSnowThresholdCm(value)
            binding.thresholdValue.text = getString(R.string.threshold_value, value.toInt())
        }
        binding.alertsSwitch.isChecked = settings.alertsEnabled.value && SnowAlertNotifier.canNotify(this)
        binding.alertsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SnowAlertNotifier.canNotify(this)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settings.setAlertsEnabled(checked)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selected.collect { loc ->
                        binding.currentLocation.text = loc?.let {
                            getString(R.string.current_location, listOfNotNull(it.name, it.region, it.country).joinToString(", "))
                        } ?: getString(R.string.no_location_yet)
                    }
                }
                launch {
                    viewModel.search.collect { s ->
                        binding.searchProgress.visibility = if (s.searching) View.VISIBLE else View.GONE
                        binding.searchError.visibility = if (s.error != null) View.VISIBLE else View.GONE
                        binding.searchError.text = s.error
                        adapter.submit(s.results)
                    }
                }
            }
        }
    }

    private fun runSearch() = viewModel.search(binding.searchInput.text?.toString().orEmpty())
}

private class CityAdapter(private val onClick: (CityResult) -> Unit) : RecyclerView.Adapter<CityAdapter.Holder>() {
    private var items: List<CityResult> = emptyList()

    class Holder(val binding: ItemCityResultBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newItems: List<CityResult>) {
        items = newItems
        notifyDataSetChanged() // tiny list (max 5 results), DiffUtil not needed
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCityResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val city = items[position]
        holder.binding.cityName.text = city.label
        holder.binding.root.setOnClickListener { onClick(city) }
    }
}

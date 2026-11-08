package com.azeemzsyed.yardready.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.azeemzsyed.yardready.R
import com.azeemzsyed.yardready.data.local.MaintenanceLog
import com.azeemzsyed.yardready.data.local.TaskType
import com.azeemzsyed.yardready.databinding.ActivityHistoryBinding
import com.azeemzsyed.yardready.databinding.ItemHistoryBinding
import com.azeemzsyed.yardready.ui.Format
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.Factory }
    private val adapter = HistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        // Swipe left or right to delete a log entry
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                viewModel.delete(adapter.currentList[vh.bindingAdapterPosition])
            }
        }).attachToRecyclerView(binding.historyList)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.logs)
                    binding.emptyText.visibility = if (state.logs.isEmpty()) View.VISIBLE else View.GONE
                    val mows = state.stats.firstOrNull { it.taskType == TaskType.MOW }?.count ?: 0
                    val clears = state.stats.firstOrNull { it.taskType == TaskType.CLEAR_SNOW }?.count ?: 0
                    binding.statsText.text = getString(R.string.history_stats, mows, clears)
                }
            }
        }
    }
}

private class HistoryAdapter : ListAdapter<MaintenanceLog, HistoryAdapter.Holder>(Diff) {
    class Holder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val log = getItem(position)
        with(holder.binding) {
            taskIcon.text = if (log.taskType == TaskType.MOW) "🌱" else "❄️"
            taskName.setText(if (log.taskType == TaskType.MOW) R.string.task_mow else R.string.task_clear_snow)
            taskDate.text = Format.dateTime(log.completedAt)
        }
    }

    private object Diff : DiffUtil.ItemCallback<MaintenanceLog>() {
        override fun areItemsTheSame(a: MaintenanceLog, b: MaintenanceLog) = a.id == b.id
        override fun areContentsTheSame(a: MaintenanceLog, b: MaintenanceLog) = a == b
    }
}

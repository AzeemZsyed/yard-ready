package com.azeemzsyed.yardready.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.azeemzsyed.yardready.R
import com.azeemzsyed.yardready.databinding.ItemDailyForecastBinding
import com.azeemzsyed.yardready.domain.DailySummary
import com.azeemzsyed.yardready.ui.Format

class DailyForecastAdapter : ListAdapter<DailySummary, DailyForecastAdapter.Holder>(Diff) {

    class Holder(val binding: ItemDailyForecastBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDailyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val day = getItem(position)
        val ctx = holder.itemView.context
        with(holder.binding) {
            dayLabel.text = Format.shortDay(day.date)
            conditionIcon.text = Format.conditionEmoji(day.condition)
            tempRange.text = ctx.getString(R.string.temp_range, Format.temp(day.maxTempC), Format.temp(day.minTempC))
            precip.text = when {
                day.snowCm > 0 -> ctx.getString(R.string.daily_snow, Format.cm(day.snowCm))
                else -> ctx.getString(R.string.daily_pop, (day.maxPrecipProbability * 100).toInt())
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DailySummary>() {
        override fun areItemsTheSame(a: DailySummary, b: DailySummary) = a.date == b.date
        override fun areContentsTheSame(a: DailySummary, b: DailySummary) = a == b
    }
}

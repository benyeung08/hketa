package com.hketa.app.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.hketa.app.R

class RouteDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROUTE_ID = "route_id"
    }

    private lateinit var viewModel: RouteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        viewModel = ViewModelProvider(this)[RouteViewModel::class.java]

        val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: run {
            finish()
            return
        }

        val name = findViewById<TextView>(R.id.textDetailName)
        val meta = findViewById<TextView>(R.id.textDetailMeta)
        val fare = findViewById<TextView>(R.id.textDetailFare)
        val interval = findViewById<TextView>(R.id.textDetailInterval)
        val service = findViewById<TextView>(R.id.textDetailService)
        val stopsList = findViewById<ListView>(R.id.listStops)
        val progress = findViewById<ProgressBar>(R.id.progressDetail)
        val emptyText = findViewById<TextView>(R.id.textDetailEmpty)

        viewModel.routeDetail.observe(this) { detail ->
            if (detail == null) {
                emptyText.visibility = View.VISIBLE
                return@observe
            }
            name.text = detail.name
            meta.text = "${detail.mode} · ${detail.operator}    ${detail.origin} → ${detail.destination}"
            fare.text = "全程收費 Full fare: HK\$${detail.fullFare}"
            interval.text = "班次間距 Interval: ${detail.interval} 分鐘 mins"
            service.text = "首班/尾班 First/Last: ${detail.firstDeparture} – ${detail.lastDeparture}"

            stopsList.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                detail.stops.mapIndexed { i, s -> "${i + 1}. ${s.stopName}" },
            )
            emptyText.visibility = if (detail.stops.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(this) { progress.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.error.observe(this) {
            if (!it.isNullOrBlank()) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        viewModel.loadRoute(routeId)
    }
}

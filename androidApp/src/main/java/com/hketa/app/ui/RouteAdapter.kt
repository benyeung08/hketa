package com.hketa.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.hketa.app.R
import com.hketa.app.data.model.RouteInfo
import java.text.SimpleDateFormat
import java.util.*

class RouteAdapter(context: Context, private val items: List<RouteInfo>) :
    ArrayAdapter<RouteInfo>(context, R.layout.item_route, items) {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    override fun getCount(): Int = items.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_route, parent, false)

        val route = items[position]
        view.findViewById<TextView>(R.id.textRouteName).text = route.name
        view.findViewById<TextView>(R.id.textRouteMode).text =
            "${route.mode} · ${route.operator}"
        view.findViewById<TextView>(R.id.textRoutePath).text =
            "${route.origin} → ${route.destination}"

        val fareText = if (route.fare > 0) "HK\$${route.fare}" else "—"
        view.findViewById<TextView>(R.id.textRouteFare).text = "收費 Fare: $fareText"

        val intervalText = if (route.interval > 0) "${route.interval} 分鐘 mins" else "—"
        view.findViewById<TextView>(R.id.textRouteInterval).text =
            "班次 Interval: $intervalText"

        val serviceText = if (route.firstDeparture != "—")
            "${route.firstDeparture} – ${route.lastDeparture}"
        else "—"
        view.findViewById<TextView>(R.id.textRouteService).text =
            "服務時間 Service: $serviceText"

        return view
    }
}

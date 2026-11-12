package com.hketa.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.hketa.app.R
import com.hketa.app.data.model.DataVersion

class UpdateAdapter(context: Context, private val items: List<DataVersion>) :
    ArrayAdapter<DataVersion>(context, R.layout.item_update, items) {

    override fun getCount(): Int = items.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_update, parent, false)

        val v = items[position]
        view.findViewById<TextView>(R.id.textUpdateMode).text = modeLabel(v.mode)
        view.findViewById<TextView>(R.id.textUpdateVersion).text = "版本 Version: ${v.version}"
        view.findViewById<TextView>(R.id.textUpdateTime).text =
            "更新時間: ${v.generatedAt}"
        view.findViewById<TextView>(R.id.textUpdateSize).text =
            "大小 Size: ${formatSize(v.size)}"

        return view
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "BUS" -> "巴士 BUS"
        "MINIBUS" -> "小巴 MINIBUS"
        "MTR" -> "地鐵 MTR"
        "LRT" -> "輕鐵 LRT"
        else -> mode
    }

    private fun formatSize(bytes: Int): String =
        if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
}

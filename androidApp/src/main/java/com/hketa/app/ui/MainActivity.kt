package com.hketa.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.hketa.app.R
import com.hketa.app.data.model.RouteInfo

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: RouteViewModel
    private lateinit var listView: ListView
    private lateinit var progress: ProgressBar

    private var currentMode: String? = null
    private val modes = listOf("BUS", "MINIBUS", "MTR", "LRT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[RouteViewModel::class.java]

        val searchInput = findViewById<EditText>(R.id.editSearch)
        val searchBtn = findViewById<Button>(R.id.btnSearch)
        val spinner = findViewById<Spinner>(R.id.spinnerMode)
        listView = findViewById(R.id.listRoutes)
        progress = findViewById(R.id.progress)
        val emptyText = findViewById<TextView>(R.id.textEmpty)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("全部 All") + modes,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                currentMode = if (pos == 0) null else modes[pos - 1]
                viewModel.loadMode(currentMode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        searchBtn.setOnClickListener {
            val q = searchInput.text.toString().trim()
            if (q.isNotEmpty()) viewModel.search(q) else viewModel.loadMode(currentMode)
        }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val item = listView.adapter.getItem(pos) as? RouteInfo
            item?.let { openRouteDetail(it.id) }
        }

        viewModel.routes.observe(this) { routes ->
            listView.adapter = RouteAdapter(this, routes)
            emptyText.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.loading.observe(this) { progress.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.error.observe(this) {
            if (!it.isNullOrBlank()) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        viewModel.loadAll()
    }

    private fun openRouteDetail(routeId: String) {
        val intent = Intent(this, RouteDetailActivity::class.java)
        intent.putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, routeId)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}

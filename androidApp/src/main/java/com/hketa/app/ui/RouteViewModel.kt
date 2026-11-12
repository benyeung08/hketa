package com.hketa.app.ui

import androidx.lifecycle.*
import com.hketa.app.data.api.RouteApiService
import com.hketa.app.data.model.*
import com.hketa.app.data.repository.RouteRepository
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RouteViewModel : ViewModel() {

    private val repository: RouteRepository

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(com.hketa.app.HkEtaApp.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = RouteRepository(retrofit.create(RouteApiService::class.java))
    }

    /* ------------------------- route list ------------------------- */

    private val _routes = MutableLiveData<List<RouteInfo>>()
    val routes: LiveData<List<RouteInfo>> = _routes

    private val _routeDetail = MutableLiveData<RouteWithStops?>()
    val routeDetail: LiveData<RouteWithStops?> = _routeDetail

    /* --------------------------- updates -------------------------- */

    private val _manifest = MutableLiveData<UpdateManifest?>()
    val manifest: LiveData<UpdateManifest?> = _manifest

    private val _lastResult = MutableLiveData<UpdateResult?>()
    val lastResult: LiveData<UpdateResult?> = _lastResult

    private val _updateSummary = MutableLiveData<UpdateSummary?>()
    val updateSummary: LiveData<UpdateSummary?> = _updateSummary

    /* --------------------------- common --------------------------- */

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /* ---------------------------- API ----------------------------- */

    fun loadAll() = loadMode(null)

    fun loadMode(mode: String?) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _routes.value = if (mode.isNullOrBlank()) {
                    repository.getAllRoutes()
                } else {
                    repository.getRoutesByMode(mode)
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "failed to load routes"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadRoute(routeId: String) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _routeDetail.value = repository.getRoute(routeId)
            } catch (t: Throwable) {
                _error.value = t.message ?: "failed to load route detail"
            } finally {
                _loading.value = false
            }
        }
    }

    fun search(query: String) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _routes.value = repository.searchRoutes(query)
            } catch (t: Throwable) {
                _error.value = t.message ?: "search failed"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadManifest() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _manifest.value = repository.getManifest()
            } catch (t: Throwable) {
                _error.value = t.message ?: "failed to load update manifest"
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateMode(mode: String) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _lastResult.value = repository.updateMode(mode)
                refreshManifest()
            } catch (t: Throwable) {
                _error.value = t.message ?: "update failed"
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateAll() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _updateSummary.value = repository.updateAll()
                refreshManifest()
            } catch (t: Throwable) {
                _error.value = t.message ?: "update-all failed"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun refreshManifest() {
        _manifest.value = repository.getManifest()
    }
}

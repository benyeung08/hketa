package com.hketa.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hketa.app.data.api.EtaApiService
import com.hketa.app.data.model.EtaEntry
import com.hketa.app.data.repository.EtaRepository
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EtaViewModel : ViewModel() {

    private val repository: EtaRepository

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(com.hketa.app.HkEtaApp.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = EtaRepository(retrofit.create(EtaApiService::class.java))
    }

    private val _entries = MutableLiveData<List<EtaEntry>>()
    val entries: LiveData<List<EtaEntry>> = _entries

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun load(stopId: String, operators: String? = null) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _entries.value = repository.getEtas(stopId, operators)
            } catch (t: Throwable) {
                _error.value = t.message ?: "failed to load ETAs"
            } finally {
                _loading.value = false
            }
        }
    }
}

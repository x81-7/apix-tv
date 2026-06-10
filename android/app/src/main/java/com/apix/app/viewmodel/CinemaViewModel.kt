package com.apix.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apix.app.data.AppSettings
import com.apix.app.data.CinemaRepository
import com.apix.app.data.HomeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CinemaViewModel(app: Application) : AndroidViewModel(app) {

    private val _homeState = MutableStateFlow(HomeData())
    val homeState: StateFlow<HomeData> = _homeState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadCinemaData(appMode: String, externalUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // نستخدم CinemaRepository الخاص بك لجلب البيانات
                val settings = AppSettings(appMode = appMode, externalSourceUrl = externalUrl)
                val data = CinemaRepository.loadHome(settings)
                _homeState.value = data
            } catch (e: Exception) {
                // خطأ صامت في حالة عدم توفر اتصال
            } finally {
                _isLoading.value = false
            }
        }
    }
}

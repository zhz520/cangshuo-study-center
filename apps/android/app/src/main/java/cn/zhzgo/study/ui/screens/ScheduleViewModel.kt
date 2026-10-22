package cn.zhzgo.study.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.ScheduleCell
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(val cells: List<ScheduleCell>) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.create(application)
    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        fetchSchedule()
    }

    fun fetchSchedule() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            try {
                val cells = apiService.getSchedule()
                _uiState.value = ScheduleUiState.Success(cells)
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState.Error(e.message ?: "未知错误")
            }
        }
    }
}

package com.lingyicute.orientationlock.ui

import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingyicute.orientationlock.data.OrientationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val orientationRepository: OrientationRepository
) : ViewModel() {

    private val _orientation = MutableStateFlow(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    val orientation: StateFlow<Int> = _orientation

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    init {
        viewModelScope.launch {
            _orientation.value = orientationRepository.getCurrentOrientation()
            _isServiceRunning.value = orientationRepository.isServiceRunning()
        }
    }

    fun setOrientation(orientation: Int) {
        viewModelScope.launch {
            orientationRepository.setOrientation(orientation)
            _orientation.value = orientation
            _isServiceRunning.value = orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
} 
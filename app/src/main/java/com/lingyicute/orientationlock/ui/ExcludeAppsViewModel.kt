package com.lingyicute.orientationlock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingyicute.orientationlock.data.OrientationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExcludeAppsViewModel @Inject constructor(
    private val repository: OrientationRepository
) : ViewModel() {

    val apps = repository.getInstalledApps()

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            repository.setAppExcluded(packageName, excluded)
        }
    }
} 
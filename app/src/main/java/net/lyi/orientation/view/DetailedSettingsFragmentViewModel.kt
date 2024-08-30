/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.lyi.orientation.control.FunctionButton
import net.lyi.orientation.control.Orientation
import net.lyi.orientation.control.mapOrientation
import net.lyi.orientation.settings.IconShape
import net.lyi.orientation.settings.PreferenceRepository
import javax.inject.Inject

@HiltViewModel
class DetailedSettingsFragmentViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    private val orientationPreferenceRepository =
        preferenceRepository.orientationPreferenceRepository
    private val controlPreferenceRepository =
        preferenceRepository.controlPreferenceRepository
    private val designPreferenceRepository =
        preferenceRepository.designPreferenceRepository
    private val menuPreferenceRepository =
        preferenceRepository.menuPreferenceRepository

    val menu = preferenceRepository.menuPreferenceFlow
        .asLiveData()
    val sample = combine(
        preferenceRepository.actualOrientationPreferenceFlow,
        preferenceRepository.designPreferenceFlow,
        ::Pair
    ).asLiveData()
    val orientation = preferenceRepository.orientationPreferenceFlow.asLiveData()
    val control = preferenceRepository.controlPreferenceFlow.asLiveData()
    val design = preferenceRepository.designPreferenceFlow.asLiveData()

    fun updateOrientation(orientation: Orientation) {
        viewModelScope.launch {
            orientationPreferenceRepository.updateOrientationManually(orientation)
        }
    }

    fun updateNotifySecret(secret: Boolean) {
        viewModelScope.launch {
            controlPreferenceRepository.updateNotifySecret(secret)
        }
    }

    fun updateUseBlankIcon(use: Boolean) {
        viewModelScope.launch {
            controlPreferenceRepository.updateUseBlankIcon(use)
        }
    }

    fun updateForeground(color: Int) {
        viewModelScope.launch {
            designPreferenceRepository.updateForeground(color)
        }
    }

    fun updateBackground(color: Int) {
        viewModelScope.launch {
            designPreferenceRepository.updateBackground(color)
        }
    }

    fun updateForegroundSelected(color: Int) {
        viewModelScope.launch {
            designPreferenceRepository.updateForegroundSelected(color)
        }
    }

    fun updateBackgroundSelected(color: Int) {
        viewModelScope.launch {
            designPreferenceRepository.updateBackgroundSelected(color)
        }
    }

    fun updateBase(color: Int) {
        viewModelScope.launch {
            designPreferenceRepository.updateBase(color)
        }
    }

    fun resetTheme() {
        viewModelScope.launch {
            designPreferenceRepository.resetTheme()
        }
    }

    fun updateShape(shape: IconShape) {
        viewModelScope.launch {
            designPreferenceRepository.updateShape(shape)
        }
    }

    fun updateFunctions(functions: List<FunctionButton>) {
        viewModelScope.launch {
            designPreferenceRepository.updateFunctions(functions)
        }
    }

    fun updateWarnSystemRotate(warn: Boolean) {
        viewModelScope.launch {
            menuPreferenceRepository.updateWarnSystemRotate(warn)
        }
    }

    fun updateOrientationWhenPowerIsConnected(orientation: Orientation) {
        viewModelScope.launch {
            orientationPreferenceRepository.updateOrientationWhenPowerIsConnected(orientation)
        }
    }

    fun adjustOrientation() {
        preferenceRepository.scope.launch {
            val orientationFlow = preferenceRepository.orientationPreferenceFlow
            val designFlow = preferenceRepository.designPreferenceFlow
            val (orientation, design) = combine(orientationFlow, designFlow, ::Pair).first()
            val candidate = design.functions.mapOrientation()
            if (!candidate.contains(orientation.orientation)) {
                val adjusted = candidate.firstOrNull() ?: Orientation.UNSPECIFIED
                orientationPreferenceRepository.updateOrientation(adjusted)
            }
        }
    }
}

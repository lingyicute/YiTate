/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view.dialog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.lyi.orientation.settings.PreferenceRepository
import net.lyi.orientation.settings.ReviewPreferenceRepository
import javax.inject.Inject

@HiltViewModel
class ReviewDialogViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    private val reviewPreferenceRepository: ReviewPreferenceRepository =
        preferenceRepository.reviewPreferenceRepository

    fun onReview() {
        preferenceRepository.scope.launch {
            reviewPreferenceRepository.updateReviewed(true)
        }
    }

    fun onReport() {
        preferenceRepository.scope.launch {
            reviewPreferenceRepository.updateReported(true)
        }
    }

    fun onCancel() {
        preferenceRepository.scope.launch {
            reviewPreferenceRepository.inclementCancelCount()
        }
    }
}

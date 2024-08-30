/*
 * Copyright (c) 2019 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.review

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.lyi.orientation.control.Orientation
import net.lyi.orientation.settings.OrientationPreference
import net.lyi.orientation.settings.PreferenceRepository
import net.lyi.orientation.settings.ReviewPreference
import net.lyi.orientation.view.dialog.ReviewDialog
import java.util.concurrent.TimeUnit

object ReviewRequest {
    private const val ORIENTATION_CHANGE_COUNT = 10
    private val INTERVAL_FIRST_REVIEW = TimeUnit.DAYS.toMillis(21)
    private val INTERVAL_SECOND_REVIEW = TimeUnit.DAYS.toMillis(42)

    suspend fun updateOrientation(orientation: Orientation, preferenceRepository: PreferenceRepository) {
        val reviewPreferenceRepository = preferenceRepository.reviewPreferenceRepository
        reviewPreferenceRepository.updateFirstUseTimeIfZero(System.currentTimeMillis())
        if (orientation != Orientation.UNSPECIFIED) {
            reviewPreferenceRepository.inclementOrientationChangeCount()
        }
    }

    fun requestReviewIfNeed(fragment: Fragment, preferenceRepository: PreferenceRepository) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            yield()
            val orientationFlow = preferenceRepository.orientationPreferenceFlow
            val reviewFlow = preferenceRepository.reviewPreferenceFlow
            val (orientation, review) = combine(orientationFlow, reviewFlow, ::Pair).first()
            requestReviewIfNeed(fragment, orientation, review, preferenceRepository)
        }
    }

    private suspend fun requestReviewIfNeed(
        fragment: Fragment,
        orientation: OrientationPreference,
        review: ReviewPreference,
        preferenceRepository: PreferenceRepository
    ) {
        if (!fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!orientation.enabled) return
        if (review.reported) return
        if (review.reviewed) return
        if (review.cancelCount >= 2) return
        if (review.orientationChangeCount < ORIENTATION_CHANGE_COUNT) return
        val now = System.currentTimeMillis()
        if (review.cancelCount == 0 &&
            now - review.firstUseTime < INTERVAL_FIRST_REVIEW + review.intervalRandomFactor
        ) {
            return
        }
        if (review.cancelCount == 1 &&
            now - review.firstReviewTime < INTERVAL_SECOND_REVIEW
        ) {
            return
        }
        if (ReviewDialog.show(fragment) && review.cancelCount == 0) {
            preferenceRepository
                .reviewPreferenceRepository
                .updateFirstReviewTime(now)
        }
    }
}

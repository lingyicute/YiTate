/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

data class ReviewPreference(
    val intervalRandomFactor: Long,
    val firstUseTime: Long,
    val firstReviewTime: Long,
    val orientationChangeCount: Int,
    val cancelCount: Int,
    val reviewed: Boolean,
    val reported: Boolean,
)

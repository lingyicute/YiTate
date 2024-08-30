/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

data class MenuPreference(
    val warnSystemRotate: Boolean,
    val nightMode: Int,
    val shouldShowAllApp: Boolean,
    val notificationPermissionRequested: Boolean,
)

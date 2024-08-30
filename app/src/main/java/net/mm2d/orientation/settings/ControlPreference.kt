/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ControlPreference(
    val shouldNotifySecret: Boolean,
    val shouldUseBlankIcon: Boolean,
) : Parcelable

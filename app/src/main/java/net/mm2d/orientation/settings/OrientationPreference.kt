/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.lyi.orientation.control.Orientation

@Parcelize
data class OrientationPreference(
    val enabled: Boolean,
    val orientation: Orientation,
    val isLandscapeDevice: Boolean,
    val shouldControlByForegroundApp: Boolean,
    val orientationWhenPowerIsConnected: Orientation,
) : Parcelable

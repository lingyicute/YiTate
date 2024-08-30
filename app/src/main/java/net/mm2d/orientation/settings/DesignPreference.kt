/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.lyi.orientation.control.FunctionButton

@Parcelize
data class DesignPreference(
    val foreground: Int,
    val background: Int,
    val foregroundSelected: Int,
    val backgroundSelected: Int,
    val base: Int,
    val shape: IconShape,
    val functions: List<FunctionButton>,
) : Parcelable

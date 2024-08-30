/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import net.lyi.orientation.control.Orientation

data class OrientationRequest(
    val orientation: Orientation = Orientation.INVALID,
    val timestamp: Long = System.currentTimeMillis(),
)

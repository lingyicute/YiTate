/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.content.Context
import android.content.res.Configuration

fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/*
 * Copyright (c) 2022 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.content.Intent
import android.os.Build

inline fun <reified T> Intent.getParcelableExtraSafely(name: String): T? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }.getOrNull()

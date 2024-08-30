/*
 * Copyright (c) 2022 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.os.Build
import android.os.Bundle
import java.io.Serializable

inline fun <reified T : Serializable> Bundle.getSerializableSafely(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(key) as? T
    }

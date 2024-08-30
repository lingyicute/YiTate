/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

object Powers {
    fun isInteractive(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isInteractive == true
}

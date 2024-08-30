/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

object Toaster {
    fun showLong(context: Context, @StringRes stringRes: Int) {
        Toast.makeText(context.applicationContext, stringRes, Toast.LENGTH_LONG).show()
    }
}

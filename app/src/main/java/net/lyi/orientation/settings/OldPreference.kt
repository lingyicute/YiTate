/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import android.content.Context
import net.lyi.android.orientationfaker.BuildConfig

object OldPreference {
    fun deleteAll(context: Context) {
        context.deleteSharedPreferences(BuildConfig.APPLICATION_ID + ".Main")
        context.deleteSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences")
        context.deleteSharedPreferences(BuildConfig.APPLICATION_ID + ".b")
    }
}

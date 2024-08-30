/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

import androidx.appcompat.app.AppCompatDelegate
import net.lyi.android.orientationfaker.R

object NightModes {
    private val modeTextMap: Map<Int, Int> = mapOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.menu_description_app_theme_system,
        AppCompatDelegate.MODE_NIGHT_NO to R.string.menu_description_app_theme_light,
        AppCompatDelegate.MODE_NIGHT_YES to R.string.menu_description_app_theme_dark,
    )

    fun getTextId(mode: Int): Int = modeTextMap[mode] ?: 0
}

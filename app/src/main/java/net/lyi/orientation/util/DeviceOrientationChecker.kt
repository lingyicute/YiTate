/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.Display
import android.view.Surface
import net.lyi.orientation.settings.PreferenceRepository

object DeviceOrientationChecker {
    suspend fun check(activity: Activity, preferenceRepository: PreferenceRepository) {
        val display = getDisplay(activity) ?: return
        val rotation = display.rotation
        val orientation = activity.resources.configuration.orientation
        val isLandscapeDevice = if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            orientation == Configuration.ORIENTATION_LANDSCAPE
        } else {
            orientation == Configuration.ORIENTATION_PORTRAIT
        }
        preferenceRepository
            .orientationPreferenceRepository
            .updateLandscapeDevice(isLandscapeDevice)
    }

    private fun getDisplay(activity: Activity): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
}

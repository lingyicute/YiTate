/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.control

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.AndroidEntryPoint
import net.lyi.orientation.settings.PreferenceRepository
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundPackageReceiver : BroadcastReceiver() {
    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var foregroundPackageSettings: ForegroundPackageSettings

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_FOREGROUND_PACKAGE) ?: return
        preferenceRepository.updatePackageOrientation(foregroundPackageSettings.get(name))
    }

    companion object {
        private const val ACTION_FOREGROUND_PACKAGE = "ACTION_FOREGROUND_PACKAGE"
        private const val EXTRA_FOREGROUND_PACKAGE = "EXTRA_FOREGROUND_PACKAGE"

        fun register(application: Application) {
            application.registerReceiver(ForegroundPackageReceiver(), IntentFilter(ACTION_FOREGROUND_PACKAGE))
        }

        fun update(context: Context, packageName: String) {
            context.sendBroadcast(Intent(ACTION_FOREGROUND_PACKAGE).also {
                it.putExtra(EXTRA_FOREGROUND_PACKAGE, packageName)
            })
        }
    }
}

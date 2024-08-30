/*
 * Copyright (c) 2018 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import dagger.hilt.android.HiltAndroidApp
import net.lyi.orientation.control.ControlStatusReceiver
import net.lyi.orientation.control.ForegroundPackageReceiver
import net.lyi.orientation.control.ForegroundPackageSettings
import net.lyi.orientation.service.MainService
import net.lyi.orientation.service.PowerConnectionReceiver
import net.lyi.orientation.settings.PreferenceRepository
import net.lyi.orientation.settings.WidgetSettingsRepository
import net.lyi.orientation.tabs.CustomTabsHelper
import net.lyi.orientation.view.notification.NotificationHelper
import net.lyi.orientation.view.widget.CustomWidgetProvider
import net.lyi.orientation.view.widget.WidgetProvider
import javax.inject.Inject

@HiltAndroidApp
@Suppress("unused")
open class App : Application() {
    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var foregroundPackageSettings: ForegroundPackageSettings

    @Inject
    lateinit var widgetSettingsRepository: WidgetSettingsRepository

    override fun onCreate() {
        super.onCreate()
        initializeOverrideWhenDebug()
        MainService.initialize(this, preferenceRepository, foregroundPackageSettings)
        NotificationHelper.createChannel(this)
        CustomTabsHelper.initialize(this)
        WidgetProvider.initialize(this, preferenceRepository)
        CustomWidgetProvider.initialize(this, preferenceRepository, widgetSettingsRepository)
        PowerConnectionReceiver.initialize(this, preferenceRepository)
        ControlStatusReceiver.register(this)
        ForegroundPackageReceiver.register(this)
    }

    protected open fun initializeOverrideWhenDebug() {
        setUpStrictMode()
    }

    private fun setUpStrictMode() {
        StrictMode.setThreadPolicy(ThreadPolicy.LAX)
        StrictMode.setVmPolicy(VmPolicy.LAX)
    }
}

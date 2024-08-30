/*
 * Copyright (c) 2018 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.service

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.lyi.android.orientationfaker.R
import net.lyi.orientation.control.ForegroundPackageChecker
import net.lyi.orientation.control.ForegroundPackageReceiver
import net.lyi.orientation.control.ForegroundPackageSettings
import net.lyi.orientation.control.OrientationHelper
import net.lyi.orientation.entity.Quadruple
import net.lyi.orientation.settings.ControlPreference
import net.lyi.orientation.settings.DesignPreference
import net.lyi.orientation.settings.OrientationPreference
import net.lyi.orientation.settings.PreferenceRepository
import net.lyi.orientation.util.SystemSettings
import net.lyi.orientation.util.Toaster
import net.lyi.orientation.util.getParcelableExtraSafely
import net.lyi.orientation.view.notification.NotificationHelper
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {
    @Inject
    lateinit var orientationHelper: OrientationHelper

    private val controlPreferenceFlow: MutableSharedFlow<ControlPreference> = MutableSharedFlow(replay = 1)
    private val designPreferenceFlow: MutableSharedFlow<DesignPreference> = MutableSharedFlow(replay = 1)
    private val orientationPreferenceFlow: MutableSharedFlow<OrientationPreference> = MutableSharedFlow(replay = 1)
    private val foregroundPackageEmptyFlow: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 1)
    private var checker: ForegroundPackageChecker? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.startForegroundEmpty(this)
        lifecycleScope.launch {
            orientationPreferenceFlow.collect { preferences ->
                if (preferences.enabled) {
                    orientationHelper.update(preferences.orientation, preferences.isLandscapeDevice)
                } else {
                    stop()
                }
            }
        }
        lifecycleScope.launch {
            combine(
                orientationPreferenceFlow,
                foregroundPackageEmptyFlow,
            ) { orientation: OrientationPreference, empty: Boolean ->
                orientation.enabled && orientation.shouldControlByForegroundApp && !empty
            }.collect {
                startForegroundChecker(it)
            }
        }
        lifecycleScope.launch {
            combine(
                orientationPreferenceFlow,
                controlPreferenceFlow,
                designPreferenceFlow,
                ::Triple
            ).collect { (orientation, control, design) ->
                NotificationHelper.startForeground(this@MainService, orientation, control, design)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val orientation = intent?.getParcelableExtraSafely<OrientationPreference>(EXTRA_ORIENTATION)?.also {
            orientationPreferenceFlow.tryEmit(it)
        }
        intent?.getParcelableExtraSafely<ControlPreference>(EXTRA_CONTROL)?.also {
            controlPreferenceFlow.tryEmit(it)
        }
        intent?.getParcelableExtraSafely<DesignPreference>(EXTRA_DESIGN)?.also {
            designPreferenceFlow.tryEmit(it)
        }
        intent?.getBooleanExtra(EXTRA_FOREGROUND_SETTING_EMPTY, false)?.let {
            foregroundPackageEmptyFlow.tryEmit(it)
        }
        if (!SystemSettings.canDrawOverlays(this) || orientation?.enabled == false) {
            stop()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun stop() {
        NotificationHelper.stopForeground(this)
        orientationHelper.cancel()
        stopForegroundChecker()
        stopSelf()
    }

    private fun startForegroundChecker(enable: Boolean) {
        if (checker != null) {
            if (!enable) {
                stopForegroundChecker()
            }
            return
        }
        if (!enable) return
        if (!SystemSettings.hasUsageAccessPermission(this)) {
            Toaster.showLong(this, R.string.toast_no_permission_to_usage_access)
            return
        }
        checker = ForegroundPackageChecker(this) {
            ForegroundPackageReceiver.update(this, it)
        }.also { checker ->
            checker.start()
        }
    }

    private fun stopForegroundChecker() {
        checker?.destroy()
        checker = null
    }

    companion object {
        private const val EXTRA_ORIENTATION = "EXTRA_ORIENTATION"
        private const val EXTRA_CONTROL = "EXTRA_CONTROL"
        private const val EXTRA_DESIGN = "EXTRA_DESIGN"
        private const val EXTRA_FOREGROUND_SETTING_EMPTY = "EXTRA_FOREGROUND_SETTING_EMPTY"

        fun initialize(
            c: Context,
            preferenceRepository: PreferenceRepository,
            foregroundPackageSettings: ForegroundPackageSettings
        ) {
            val context = c.applicationContext
            preferenceRepository.scope.launch {
                combine(
                    preferenceRepository.actualOrientationPreferenceFlow,
                    preferenceRepository.controlPreferenceFlow,
                    preferenceRepository.designPreferenceFlow,
                    foregroundPackageSettings.emptyFlow(),
                    ::Quadruple
                ).collect { (orientation, control, design, empty) ->
                    runCatching { start(context, orientation, control, design, empty) }
                }
            }
        }

        private fun start(
            context: Context,
            orientation: OrientationPreference,
            control: ControlPreference,
            design: DesignPreference,
            empty: Boolean,
        ) {
            val intent = Intent(context, MainService::class.java).also {
                it.putExtra(EXTRA_ORIENTATION, orientation)
                it.putExtra(EXTRA_CONTROL, control)
                it.putExtra(EXTRA_DESIGN, design)
                it.putExtra(EXTRA_FOREGROUND_SETTING_EMPTY, empty)
            }
            context.startForegroundService(intent)
        }
    }
}

package com.lingyicute.orientationlock.data

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import com.lingyicute.orientationlock.preference.PreferenceManager
import com.lingyicute.orientationlock.service.YiTateService
import com.lingyicute.orientationlock.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

interface OrientationRepository {
    suspend fun getCurrentOrientation(): Int
    suspend fun setOrientation(orientation: Int)
    suspend fun isServiceRunning(): Boolean
    fun getInstalledApps(): Flow<List<ExcludedApp>>
    suspend fun setAppExcluded(packageName: String, excluded: Boolean)
    suspend fun isAppExcluded(packageName: String): Boolean
}

@Singleton
class OrientationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val notificationManager: NotificationManager
) : OrientationRepository {

    override suspend fun getCurrentOrientation(): Int {
        return if (PermissionUtils.isDrawOverlaysPermissionGranted(context)) {
            preferenceManager.getOrientation()
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override suspend fun setOrientation(orientation: Int) {
        preferenceManager.setOrientation(orientation)
        val intent = Intent(context, YiTateService::class.java).apply {
            action = YiTateService.ACTION_SET_ORIENTATION
            putExtra(YiTateService.KEY_ORIENTATION, orientation)
        }

        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            context.stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override suspend fun isServiceRunning(): Boolean {
        // 在 Android O 及以上版本，通过检查通知来判断服务是否运行
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.activeNotifications.any { 
                it.id == YiTateService.NOTIFICATION_ID && 
                it.packageName == context.packageName 
            }
        } else {
            // 在旧版本上使用传统方法
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == YiTateService::class.java.name }
        }
    }

    override fun getInstalledApps(): Flow<List<ExcludedApp>> = flow {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { !isSystemApp(it.flags) }
            .map { appInfo ->
                ExcludedApp(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isExcluded = preferenceManager.isAppExcluded(appInfo.packageName)
                )
            }
            .sortedBy { it.appName }
        emit(installedApps)
    }.flowOn(Dispatchers.IO)

    override suspend fun setAppExcluded(packageName: String, excluded: Boolean) {
        preferenceManager.setAppExcluded(packageName, excluded)
    }

    override suspend fun isAppExcluded(packageName: String): Boolean {
        return preferenceManager.isAppExcluded(packageName)
    }

    private fun isSystemApp(flags: Int): Boolean {
        return flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    }
} 
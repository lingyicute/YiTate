package com.lingyicute.orientationlock.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Space
import androidx.core.app.NotificationCompat
import com.lingyicute.orientationlock.BuildConfig
import com.lingyicute.orientationlock.R
import com.lingyicute.orientationlock.preference.PreferenceManager
import com.lingyicute.orientationlock.ui.MainActivity
import com.lingyicute.orientationlock.utils.SimpleLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class YiTateService : Service() {

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private var holderView: Space? = null
    private var currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> removeOrientationLayout()
                Intent.ACTION_SCREEN_ON -> setSystemOrientation(currentOrientation)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        SimpleLog.d(TAG, "register service receiver")
    }

    override fun onDestroy() {
        SimpleLog.d(TAG, "unregister service receiver")
        unregisterReceiver(screenReceiver)
        removeOrientationLayout()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_ORIENTATION -> {
                val orientation = intent.getIntExtra(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                if (orientation != currentOrientation) {
                    setSystemOrientation(orientation)
                    updateNotification()
                }
            }
            ACTION_QUICK_CHANGE -> {
                val newOrientation = when (currentOrientation) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                setSystemOrientation(newOrientation)
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.running_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val mainIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val quickChangeIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, YiTateService::class.java).apply {
                action = ACTION_QUICK_CHANGE
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val orientationText = when (currentOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> getString(R.string.orientation_portrait)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> getString(R.string.orientation_landscape)
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> getString(R.string.orientation_reverse_portrait)
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> getString(R.string.orientation_reverse_landscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> getString(R.string.orientation_sensor_portrait)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> getString(R.string.orientation_sensor_landscape)
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> getString(R.string.orientation_full_sensor)
            else -> getString(R.string.orientation_default)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.running_notification_title))
            .setContentText(getString(R.string.current_orientation, orientationText))
            .setSmallIcon(R.drawable.ic_stat_orientation)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentIntent(mainIntent)
            .addAction(
                R.drawable.ic_screen_rotation,
                getString(R.string.quick_change),
                quickChangeIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun setSystemOrientation(screenOrientation: Int) {
        SimpleLog.d(TAG, "set system orientation: $screenOrientation")

        if (screenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            removeOrientationLayout()
            return
        }

        currentOrientation = screenOrientation

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.RGBA_8888
        )
        params.screenOrientation = currentOrientation

        if (holderView == null) {
            holderView = Space(this).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                isLongClickable = false
                visibility = View.GONE
            }
            windowManager.addView(holderView, params)
        }

        holderView?.let {
            windowManager.updateViewLayout(it, params)
            it.visibility = View.VISIBLE
        }
    }

    private fun removeOrientationLayout() {
        SimpleLog.d(TAG, "remove system orientation")
        holderView?.let {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(it)
            holderView = null
        }
    }

    companion object {
        private const val TAG = "YiTateService"
        private const val NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.notification"
        private const val NOTIFICATION_ID = 1

        const val ACTION_SET_ORIENTATION = "${BuildConfig.APPLICATION_ID}.action.SET_ORIENTATION"
        const val ACTION_QUICK_CHANGE = "${BuildConfig.APPLICATION_ID}.action.QUICK_CHANGE"
        const val KEY_ORIENTATION = "orientation"
    }
} 
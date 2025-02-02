package com.lingyicute.orientationlock.preference

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import androidx.core.content.edit

class PreferenceManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrientation(): Int {
        return prefs.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    fun setOrientation(orientation: Int) {
        prefs.edit { putInt(KEY_ORIENTATION, orientation) }
    }

    fun isAppExcluded(packageName: String): Boolean {
        return prefs.getBoolean("${KEY_EXCLUDED_APP_PREFIX}$packageName", false)
    }

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        prefs.edit { putBoolean("${KEY_EXCLUDED_APP_PREFIX}$packageName", excluded) }
    }

    companion object {
        private const val PREFS_NAME = "orientation_prefs"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_EXCLUDED_APP_PREFIX = "excluded_app_"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }
} 
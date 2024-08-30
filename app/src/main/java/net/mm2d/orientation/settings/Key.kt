/*
 * Copyright (c) 2020 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.settings

import android.content.Context
import androidx.annotation.Keep
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import net.mm2d.android.orientationfaker.BuildConfig
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

interface Key {
    enum class Package : Key {
        DATA_VERSION_INT,
        VERSION_AT_INSTALL_INT,
        VERSION_AT_LAST_LAUNCHED_INT,
        VERSION_BEFORE_UPDATE_INT,
    }

    enum class Review : Key {
        DATA_VERSION_INT,
        INTERVAL_RANDOM_FACTOR_LONG,
        FIRST_USE_TIME_LONG,
        FIRST_REVIEW_TIME_LONG,
        ORIENTATION_CHANGE_COUNT_INT,
        CANCEL_COUNT_INT,
        REPORTED_BOOLEAN,
        REVIEWED_BOOLEAN,
    }

    enum class Orientation : Key {
        DATA_VERSION_INT,
        ENABLED_BOOLEAN,
        ORIENTATION_INT,
        LANDSCAPE_DEVICE_BOOLEAN,
        CONTROL_BY_FOREGROUND_APP_BOOLEAN,
        ORIENTATION_WHEN_POWER_IS_CONNECTED_INT,
    }

    enum class Control : Key {
        DATA_VERSION_INT,
        NOTIFY_SECRET_BOOLEAN,
        USE_BLANK_ICON_BOOLEAN,
    }

    enum class Design : Key {
        DATA_VERSION_INT,
        FOREGROUND_INT,
        BACKGROUND_INT,
        FOREGROUND_SELECTED_INT,
        BACKGROUND_SELECTED_INT,
        BASE_INT,
        SHAPE_STRING,
        FUNCTION_BUTTONS_STRING,

        @Deprecated("removed: 6.0.0")
        ICONIZE_BOOLEAN,

        @Deprecated("removed: 6.0.0")
        ORIENTATION_LIST_STRING,

        @Deprecated("removed: 6.0.0")
        SHOW_SETTINGS_BOOLEAN,
    }

    @Keep
    enum class Menu : Key {
        DATA_VERSION_INT,
        AUTO_ROTATE_WARNING_BOOLEAN,
        NIGHT_MODE_INT,
        SHOW_ALL_APPS_BOOLEAN,
        NOTIFICATION_PERMISSION_REQUESTED_BOOLEAN,
    }
}

enum class DataStoreFile {
    PACKAGE,
    REVIEW,
    ORIENTATION,
    CONTROL,
    DESIGN,
    MENU,
    ;

    fun fileName(): String =
        BuildConfig.APPLICATION_ID + "." + name.lowercase()
}

fun preferences(
    file: DataStoreFile,
    migrations: List<DataMigration<Preferences>> = listOf(),
): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(
        name = file.fileName(),
        produceMigrations = { migrations },
    )

fun Preferences.edit(editor: (preferences: MutablePreferences) -> Unit): Preferences =
    toMutablePreferences().also(editor).toPreferences()

fun <K> K.intKey(): Preferences.Key<Int>
    where K : Enum<*>,
          K : Key {
    if (BuildConfig.DEBUG) {
        checkSuffix(Int::class)
    }
    return intPreferencesKey(name)
}

fun <K> K.stringKey(): Preferences.Key<String>
    where K : Enum<*>,
          K : Key {
    if (BuildConfig.DEBUG) {
        checkSuffix(String::class)
    }
    return stringPreferencesKey(name)
}

fun <K> K.booleanKey(): Preferences.Key<Boolean>
    where K : Enum<*>,
          K : Key {
    if (BuildConfig.DEBUG) {
        checkSuffix(Boolean::class)
    }
    return booleanPreferencesKey(name)
}

fun <K> K.longKey(): Preferences.Key<Long>
    where K : Enum<*>,
          K : Key {
    if (BuildConfig.DEBUG) {
        checkSuffix(Long::class)
    }
    return longPreferencesKey(name)
}

private const val SUFFIX_BOOLEAN = "_BOOLEAN"
private const val SUFFIX_INT = "_INT"
private const val SUFFIX_LONG = "_LONG"
private const val SUFFIX_FLOAT = "_FLOAT"
private const val SUFFIX_STRING = "_STRING"

internal fun Enum<*>.checkSuffix(value: KClass<*>) {
    if (!BuildConfig.DEBUG) return
    when (value) {
        Boolean::class -> require(name.endsWith(SUFFIX_BOOLEAN)) {
            "$this is used for Boolean, suffix \"$SUFFIX_BOOLEAN\" is required."
        }
        Int::class -> require(name.endsWith(SUFFIX_INT)) {
            "$this is used for Int, suffix \"$SUFFIX_INT\" is required."
        }
        Long::class -> require(name.endsWith(SUFFIX_LONG)) {
            "$this is used for Long, suffix \"$SUFFIX_LONG\" is required."
        }
        Float::class -> require(name.endsWith(SUFFIX_FLOAT)) {
            "$this is used for Float, suffix \"$SUFFIX_FLOAT\" is required."
        }
        String::class -> require(name.endsWith(SUFFIX_STRING)) {
            "$this is used for String, suffix \"$SUFFIX_STRING\" is required."
        }
    }
}

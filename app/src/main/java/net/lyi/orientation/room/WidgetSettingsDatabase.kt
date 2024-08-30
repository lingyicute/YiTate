/*
 * Copyright (c) 2023 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [WidgetSettingEntity::class], version = 1)
@TypeConverters(WidgetSettingConverter::class)
abstract class WidgetSettingsDatabase : RoomDatabase() {
    abstract fun widgetSettingsDao(): WidgetSettingsDao
}

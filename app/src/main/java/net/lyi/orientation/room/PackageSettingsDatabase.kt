/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PackageSettingEntity::class], version = 1)
abstract class PackageSettingsDatabase : RoomDatabase() {
    abstract fun packageSettingsDao(): PackageSettingsDao
}

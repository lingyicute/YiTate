/*
 * Copyright (c) 2020 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.control

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mm2d.orientation.room.PackageSettingEntity
import net.mm2d.orientation.room.PackageSettingsDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundPackageSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val database: PackageSettingsDatabase =
        Room.databaseBuilder(context, PackageSettingsDatabase::class.java, DB_NAME).build()
    private val map: MutableMap<String, Orientation> = mutableMapOf()
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + job + exceptionHandler)
    private val emptyFlow: MutableStateFlow<Boolean> = MutableStateFlow(true)

    init {
        scope.launch {
            val all = database.packageSettingsDao().getAll()
            withContext(Dispatchers.Main) {
                all.forEach {
                    map[it.packageName] = it.orientation.toOrientation()
                }
                emptyFlow.value = map.isEmpty()
            }
        }
    }

    fun updateInstalledPackages(packages: Set<String>) {
        val list = map.keys.toMutableList()
        scope.launch {
            list.removeAll(packages)
            withContext(Dispatchers.Main) {
                list.forEach { map.remove(it) }
            }
            list.forEach { database.packageSettingsDao().delete(it) }
        }
    }

    fun emptyFlow(): Flow<Boolean> = emptyFlow

    fun get(packageName: String): Orientation = map.getOrElse(packageName) { Orientation.INVALID }

    fun put(packageName: String, orientation: Orientation) {
        if (orientation == Orientation.INVALID) {
            map.remove(packageName)
            scope.launch {
                database.packageSettingsDao().delete(packageName)
            }
        } else {
            map[packageName] = orientation
            scope.launch {
                val entity = PackageSettingEntity(packageName, orientation.value)
                database.packageSettingsDao().insert(entity)
            }
        }
        emptyFlow.value = map.isEmpty()
    }

    fun reset() {
        map.clear()
        scope.launch {
            database.packageSettingsDao().deleteAll()
        }
        emptyFlow.value = map.isEmpty()
    }

    companion object {
        private const val DB_NAME = "package_settings.db"
    }
}

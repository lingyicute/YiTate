/*
 * Copyright (c) 2021 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.settings

data class PackagePreference(
    val versionAtInstall: Int,
    val versionAtLastLaunched: Int,
    val versionBeforeUpdate: Int,
)

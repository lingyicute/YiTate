/*
 * Copyright (c) 2021 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.settings

data class PackagePreference(
    val versionAtInstall: Int,
    val versionAtLastLaunched: Int,
    val versionBeforeUpdate: Int,
)

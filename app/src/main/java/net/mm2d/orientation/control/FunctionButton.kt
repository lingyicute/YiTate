/*
 * Copyright (c) 2023 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.control

import net.lyi.orientation.control.FunctionButton.OrientationButton
import java.io.Serializable

sealed interface FunctionButton : Serializable {
    val orientation: Orientation?
    val index: Int

    enum class OrientationButton(
        override val orientation: Orientation
    ) : FunctionButton {
        PORTRAIT(Orientation.PORTRAIT),
        LANDSCAPE(Orientation.LANDSCAPE),
        REVERSE_PORTRAIT(Orientation.REVERSE_PORTRAIT),
        REVERSE_LANDSCAPE(Orientation.REVERSE_LANDSCAPE),
        UNSPECIFIED(Orientation.UNSPECIFIED),
        FULL_SENSOR(Orientation.FULL_SENSOR),
        SENSOR_PORTRAIT(Orientation.SENSOR_PORTRAIT),
        SENSOR_LANDSCAPE(Orientation.SENSOR_LANDSCAPE),
        SENSOR_LIE_RIGHT(Orientation.SENSOR_LIE_RIGHT),
        SENSOR_LIE_LEFT(Orientation.SENSOR_LIE_LEFT),
        SENSOR_HEADSTAND(Orientation.SENSOR_HEADSTAND),
        SENSOR_FULL(Orientation.SENSOR_FULL),
        SENSOR_FORWARD(Orientation.SENSOR_FORWARD),
        SENSOR_REVERSE(Orientation.SENSOR_REVERSE),
        ;

        override val index: Int = ordinal
        override fun toString(): String = PREFIX + PREFIX_DELIMITER + name

        companion object {
            const val PREFIX = "O"
            fun of(name: String): OrientationButton? = values().find { it.name == name }
        }
    }

    enum class LauncherButton : FunctionButton {
        SETTINGS,
        ;

        override val orientation: Orientation? = null
        override val index: Int = ordinal + OrientationButton.values().size
        override fun toString(): String = PREFIX + PREFIX_DELIMITER + name

        companion object {
            const val PREFIX = "L"
            fun of(name: String): LauncherButton? = values().find { it.name == name }
        }
    }

    companion object {
        const val MAX = 6
        const val MIN = 1
        private const val VALUE_DELIMITER = ","
        private const val PREFIX_DELIMITER = ":"

        fun all(): List<FunctionButton> =
            OrientationButton.values().toList() + LauncherButton.values().toList()

        fun String?.migrateFromOrientations(): List<FunctionButton> =
            orEmpty()
                .split(VALUE_DELIMITER)
                .mapNotNull { it.toIntOrNull() }
                .mapNotNull { it.toOrientation().toFunctionButton() }

        private fun Orientation.toFunctionButton(): FunctionButton? =
            OrientationButton.values().find { it.orientation == this }

        fun String?.toFunctionButtons(): List<FunctionButton> =
            orEmpty()
                .split(VALUE_DELIMITER)
                .mapNotNull { it.toFunctionButton() }

        private fun String.toFunctionButton(): FunctionButton? {
            val split = split(PREFIX_DELIMITER)
            if (split.size != 2) return null
            val prefix = split[0]
            val name = split[1]
            return when (prefix) {
                OrientationButton.PREFIX ->
                    OrientationButton.of(name)
                LauncherButton.PREFIX ->
                    LauncherButton.of(name)
                else -> null
            }
        }

        fun List<FunctionButton>.toSerializedString(): String =
            joinToString(separator = VALUE_DELIMITER)
    }
}

fun List<FunctionButton>.mapOrientation(): List<Orientation> =
    mapNotNull { (it as? OrientationButton)?.orientation }

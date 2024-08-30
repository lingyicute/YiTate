/*
 * Copyright (c) 2018 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view

import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import net.lyi.android.orientationfaker.R
import net.lyi.orientation.control.FunctionButton
import net.lyi.orientation.control.FunctionButton.OrientationButton
import net.lyi.orientation.control.Functions
import net.lyi.orientation.settings.DesignPreference
import net.lyi.orientation.settings.OrientationPreference
import net.lyi.orientation.util.alpha
import net.lyi.orientation.util.opaque
import net.lyi.orientation.view.widget.ViewIds

class NotificationSample(view: View) {
    val buttonList: List<ButtonViews> = ViewIds.notification.map {
        ButtonViews(
            view.findViewById(it.buttonId),
            view.findViewById(it.iconId),
            view.findViewById(it.shapeId),
            OrientationButton.UNSPECIFIED,
        )
    }
    private val base = view.findViewById<View>(R.id.notification)

    fun update(orientation: OrientationPreference, design: DesignPreference) {
        base.setBackgroundColor(design.base)
        val functions = design.functions
        functions.forEachIndexed { index, value ->
            val button = buttonList[index]
            Functions.find(value)?.let {
                button.icon.setImageResource(it.icon)
                button.function = value
            }
        }
        val iconShape = design.shape
        val selectedOrientation = orientation.orientation
        buttonList.forEachIndexed { index, it ->
            it.shape.setImageResource(iconShape.iconId)
            if (it.function.orientation == selectedOrientation) {
                it.shape.setImageColor(design.backgroundSelected)
                it.icon.setImageColor(design.foregroundSelected)
            } else {
                it.shape.setImageColor(design.background)
                it.icon.setImageColor(design.foreground)
            }
            it.button.isVisible = index < functions.size
        }
    }

    private fun ImageView.setImageColor(@ColorInt color: Int) {
        setColorFilter(color.opaque())
        imageAlpha = color.alpha()
    }

    class ButtonViews(
        val button: View,
        val icon: ImageView,
        val shape: ImageView,
        var function: FunctionButton,
    )
}

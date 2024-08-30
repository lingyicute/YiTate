/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import net.lyi.android.orientationfaker.databinding.ViewCheckItemBinding
import net.lyi.orientation.control.FunctionButton
import net.lyi.orientation.control.FunctionButton.OrientationButton

class CheckItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val binding: ViewCheckItemBinding =
        ViewCheckItemBinding.inflate(LayoutInflater.from(context), this)

    var funciton: FunctionButton = OrientationButton.UNSPECIFIED

    fun setText(id: Int) {
        binding.name.setText(id)
    }

    fun setIcon(id: Int) {
        binding.icon.setImageResource(id)
    }

    var isChecked: Boolean
        get() = binding.checkbox.isChecked
        set(value) {
            binding.checkbox.isChecked = value
        }
}

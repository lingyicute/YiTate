/*
 * Copyright (c) 2023 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view.widget.config

import androidx.recyclerview.widget.RecyclerView
import net.lyi.orientation.control.Functions

interface DragHelper {
    fun setAdapter(adapter: DragItemAdapter)
    fun onAttachedToRecyclerView(recyclerView: RecyclerView)
    fun onDetachedFromRecyclerView(recyclerView: RecyclerView)
    fun itemAlpha(data: Functions.Entity): Float
    fun dragStart(holder: RecyclerView.ViewHolder, data: Functions.Entity)
}

/*
 * Copyright (c) 2020 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.view.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import net.lyi.android.orientationfaker.R
import net.lyi.android.orientationfaker.databinding.LayoutOrientationItemBinding
import net.lyi.orientation.control.Functions

class OrientationHelpDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = HelpAdapter(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerView.isVerticalFadingEdgeEnabled = true
        return AlertDialog.Builder(context)
            .setView(recyclerView)
            .setPositiveButton(R.string.ok, null)
            .create()
    }

    class HelpAdapter(
        context: Context
    ) : Adapter<ViewHolder>() {
        private val layoutInflater = LayoutInflater.from(context)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutOrientationItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int =
            Functions.orientations.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(Functions.orientations[position])
        }
    }

    class ViewHolder(
        private val binding: LayoutOrientationItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: Functions.Entity) {
            binding.icon.setImageResource(entity.icon)
            binding.name.setText(entity.label)
            binding.description.setText(entity.description)
        }
    }

    companion object {
        private const val TAG = "OrientationHelpDialog"

        fun show(fragment: Fragment) {
            val manager = fragment.childFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            OrientationHelpDialog().show(manager, TAG)
        }
    }
}

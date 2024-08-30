/*
 * Copyright (c) 2020 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.view.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import net.mm2d.android.orientationfaker.R
import net.mm2d.orientation.util.SystemSettings

class OverlayPermissionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val message = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_RAM_LOW)
        ) {
            R.string.dialog_message_overlay_permission
        } else {
            R.string.dialog_message_overlay_permission_go
        }
        return AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_title_overlay_permission)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_button_open_settings) { _, _ ->
                SystemSettings.requestOverlayPermission(activity)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        activity?.finish()
    }

    companion object {
        private const val TAG = "OverlayPermissionDialog"

        fun show(activity: Fragment) {
            val manager = activity.childFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            OverlayPermissionDialog().show(manager, TAG)
        }
    }
}

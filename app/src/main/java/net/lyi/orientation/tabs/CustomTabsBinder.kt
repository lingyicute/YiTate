/*
 * Copyright (c) 2018 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.tabs

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class CustomTabsBinder : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        CustomTabsHelper.bind()
    }

    override fun onStop(owner: LifecycleOwner) {
        CustomTabsHelper.unbind()
    }
}

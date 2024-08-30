/*
 * Copyright (c) 2023 lingyicute
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.lyi.orientation.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

fun <T> LiveData<T>.doOnNext(block: (T?) -> Unit): LiveData<T> =
    MediatorLiveData<T>().also { mediator ->
        mediator.addSource(this) {
            block(it)
            mediator.value = it
        }
    }

package com.lingyicute.orientationlock.data

data class ExcludedApp(
    val packageName: String,
    val appName: String,
    val isExcluded: Boolean = false
) 
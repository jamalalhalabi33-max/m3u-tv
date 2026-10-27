package com.example.data.model

import android.graphics.drawable.Drawable

data class VideoPlayerApp(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val icon: Drawable?,
    val isSystemDefault: Boolean = false
)

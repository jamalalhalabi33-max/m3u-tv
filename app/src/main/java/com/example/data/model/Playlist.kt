package com.example.data.model

data class Playlist(
    val id: String,
    val name: String,
    val sourceType: String, // "URL" or "FILE"
    val sourcePath: String,
    val channelCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

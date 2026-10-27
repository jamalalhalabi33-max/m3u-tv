package com.example.data.model

data class Channel(
    val id: String,
    val playlistId: String,
    val playlistName: String,
    val channelNumber: Int,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    var isFavorite: Boolean = false
)

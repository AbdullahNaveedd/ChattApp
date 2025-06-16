package com.example.chatapp.Auth.Message

import java.util.Date

data class MessageDataClass(
    val name: String,
    val description: String,
    val time: String,
    val imageResId: String,
    val notificationIconResId: Int? =null,
    val receiverId: String? = null,
    var mediaUrl: String? = null,
    var messageType: String,
    val isRead: Boolean = false,
    val rawTime: Date?
)

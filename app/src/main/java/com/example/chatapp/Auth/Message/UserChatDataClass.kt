package com.example.chatapp.Auth.Message

import java.util.Date

data class UserChatDataClass(
    val sendMessage: String? = null,
    val recieveMessage: String? = null,
    val recieverName: String? = null,
    val recieverId: String? = null,
    val recieverImage: String? = null,
    val time: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    var mediaUrl: String? = null,
    val senderId: String? = null,
    val timestamp: Date? = null,
    val userId: String? = null
)

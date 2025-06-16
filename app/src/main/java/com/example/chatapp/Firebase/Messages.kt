package com.example.chatapp.Firebase

import com.google.firebase.Timestamp

data class Messages(
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val reciverName: String = "",
    val message: String = "",
    val messageType: String = "",
    val mediaUrl: String = "",
    val timestamp: Timestamp = Timestamp.now(),
)
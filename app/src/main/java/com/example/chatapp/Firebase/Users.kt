package com.example.chatapp.Firebase

import com.google.firebase.Timestamp

data class Users(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profilePicture: String? = null,
    val createdAt: Timestamp = Timestamp.now()
)

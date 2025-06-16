package com.example.chatapp.Call

data class CallDataClass(
    val name: String,
    val description: String? = null,
    val descriptionResId: Int? = null,
    val imageResId: String? = null,
    val imageUrl: Int? = null,
    val callRedId: Int? = null,
    val videoResId: Int? = null,
    val userId: String? = null
)

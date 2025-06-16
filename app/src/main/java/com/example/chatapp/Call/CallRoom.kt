package com.example.chatapp.Call

data class CallRoom(
    val roomId: String = "",
    val createdBy: String = "",
    val participants: List<String> = emptyList(),
    val status: String = "waiting",
    val createdAt: Long = System.currentTimeMillis(),
    val tokens: Map<String, String> = emptyMap()
)
package com.example.chatapp.Call

data class CallRoom(
    val roomId: String? = null,
    val createdBy: String? = null,
    val participants: List<String> = emptyList(),
    val status: String? = null,
    val createdAt: Long = 0,
    val tokens: Map<String, String> = emptyMap(),
    val endedAt: Long? = null
)
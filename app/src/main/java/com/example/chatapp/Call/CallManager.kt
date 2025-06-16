package com.example.chatapp.Call

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.*

class CallManager {
    private val database = FirebaseDatabase.getInstance().reference
    private val LIVEKIT_API_KEY = "APIYJKVwDFd2gTv"
    private val LIVEKIT_SECRET_KEY = "RIOnqBjnnKfBllyuKUKzDhBeZU4KV84laKf9FSIwXO2B"

    fun initiateCall(
        senderId: String,
        receiverId: String,
        onRoomCreated: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val roomId = generateConsistentRoomName(senderId, receiverId)
            val callRoomRef = database.child("calls").child(roomId)

            Log.d("CallManager", "Initiating call - Room: $roomId, Sender: $senderId, Receiver: $receiverId")

            checkForActiveCall(senderId, receiverId) { existingRoomId ->
                if (existingRoomId != null) {
                    Log.d("CallManager", "Found existing active call, joining: $existingRoomId")
                    joinExistingRoom(existingRoomId, senderId, onRoomCreated, onError)
                } else {
                    callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            try {
                                if (snapshot.exists()) {
                                    val callRoom = snapshot.getValue(CallRoom::class.java)
                                    if (callRoom != null && callRoom.status != "ended") {
                                        Log.d("CallManager", "Room exists and active, joining...")
                                        joinExistingRoom(roomId, senderId, onRoomCreated, onError)
                                    } else {
                                        Log.d("CallManager", "Room exists but ended, creating new...")
                                        createNewRoom(roomId, senderId, receiverId, onRoomCreated, onError)
                                    }
                                } else {
                                    Log.d("CallManager", "Creating new room...")
                                    createNewRoom(roomId, senderId, receiverId, onRoomCreated, onError)
                                }
                            } catch (e: Exception) {
                                Log.e("CallManager", "Error in onDataChange", e)
                                onError("Database error: ${e.message}")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("CallManager", "Database cancelled: ${error.message}")
                            onError("Database error: ${error.message}")
                        }
                    })
                }
            }

        } catch (e: Exception) {
            Log.e("CallManager", "Error initiating call", e)
            onError("Failed to initiate call: ${e.message}")
        }
    }

    private fun checkForActiveCall(
        userId1: String,
        userId2: String,
        callback: (String?) -> Unit
    ) {
        try {
            database.child("calls")
                .orderByChild("status")
                .equalTo("waiting")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            for (child in snapshot.children) {
                                val callRoom = child.getValue(CallRoom::class.java)
                                if (callRoom != null) {
                                    val hasUser1 = callRoom.tokens.containsKey(userId1)
                                    val hasUser2 = callRoom.tokens.containsKey(userId2)

                                    if (hasUser1 && hasUser2) {
                                        callback(callRoom.roomId)
                                        return
                                    }
                                }
                            }
                            callback(null)
                        } catch (e: Exception) {
                            Log.e("CallManager", "Error checking active calls", e)
                            callback(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("CallManager", "Error checking active calls: ${error.message}")
                        callback(null)
                    }
                })
        } catch (e: Exception) {
            Log.e("CallManager", "Error in checkForActiveCall", e)
            callback(null)
        }
    }
    private fun createNewRoom(
        roomId: String,
        creatorId: String,
        receiverId: String,
        onRoomCreated: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            Log.d("CallManager", "Generating tokens for room: $roomId")

            val creatorToken = generateToken(roomId, creatorId)
            val receiverToken = generateToken(roomId, receiverId)

            Log.d("CallManager", "Creator token: ${creatorToken.take(50)}...")
            Log.d("CallManager", "Receiver token: ${receiverToken.take(50)}...")

            val callRoom = CallRoom(
                roomId = roomId,
                createdBy = creatorId,
                participants = listOf(creatorId),
                status = "waiting",
                createdAt = System.currentTimeMillis(),
                tokens = mapOf(
                    creatorId to creatorToken,
                    receiverId to receiverToken
                )
            )

            database.child("calls").child(roomId).setValue(callRoom)
                .addOnSuccessListener {
                    Log.d("CallManager", "Room created successfully: $roomId")
                    onRoomCreated(roomId, creatorToken)
                    listenForParticipantJoin(roomId)
                }
                .addOnFailureListener { exception ->
                    Log.e("CallManager", "Failed to create room", exception)
                    onError("Failed to create room: ${exception.message}")
                }

        } catch (e: Exception) {
            Log.e("CallManager", "Error creating room", e)
            onError("Token generation failed: ${e.message}")
        }
    }

    private fun joinExistingRoom(
        roomId: String,
        userId: String,
        onRoomJoined: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val callRoomRef = database.child("calls").child(roomId)

            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null && callRoom.status != "ended") {
                            val token = callRoom.tokens[userId]
                            if (token != null) {
                                Log.d("CallManager", "Found token for user: $userId")

                                val updatedParticipants = callRoom.participants.toMutableList()
                                if (!updatedParticipants.contains(userId)) {
                                    updatedParticipants.add(userId)
                                }

                                val updates = mapOf(
                                    "participants" to updatedParticipants,
                                    "status" to if (updatedParticipants.size >= 2) "active" else "waiting"
                                )

                                callRoomRef.updateChildren(updates)
                                    .addOnSuccessListener {
                                        Log.d("CallManager", "Successfully joined room: $roomId")
                                        onRoomJoined(roomId, token)
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.e("CallManager", "Failed to update room", exception)
                                        onError("Failed to join room: ${exception.message}")
                                    }
                            } else {
                                Log.e("CallManager", "No token found for user: $userId")
                                onError("No access token found")
                            }
                        } else {
                            Log.e("CallManager", "Room not found or ended: $roomId")
                            onError("Room not available")
                        }
                    } catch (e: Exception) {
                        Log.e("CallManager", "Error joining room", e)
                        onError("Error joining room: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CallManager", "Database error joining room", error.toException())
                    onError("Database error: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("CallManager", "Error in joinExistingRoom", e)
            onError("Failed to join room: ${e.message}")
        }
    }
    private fun generateConsistentRoomName(senderId: String, receiverId: String): String {
        val participants = listOf(senderId, receiverId).sorted()
        return "call_${participants.joinToString("_")}"
    }

    fun endCall(roomId: String, userId: String) {
        try {
            val callRoomRef = database.child("calls").child(roomId)

            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null) {
                            val updatedParticipants = callRoom.participants.toMutableList()
                            updatedParticipants.remove(userId)

                            if (updatedParticipants.isEmpty()) {
                                val updates = mapOf(
                                    "status" to "ended",
                                    "endedAt" to System.currentTimeMillis()
                                )
                                callRoomRef.updateChildren(updates)
                                Log.d("CallManager", "Room marked as ended: $roomId")

                                callRoomRef.removeValue().addOnSuccessListener {
                                    Log.d("CallManager", "Room deleted: $roomId")
                                }
                            } else {
                                val updates = mapOf(
                                    "participants" to updatedParticipants,
                                    "status" to if (updatedParticipants.size == 1) "waiting" else "active"
                                )
                                callRoomRef.updateChildren(updates)
                                Log.d("CallManager", "User left room: $roomId")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CallManager", "Error ending call", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CallManager", "Error ending call: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("CallManager", "Error in endCall", e)
        }
    }

    private fun generateToken(roomName: String, participantName: String): String {
        return try {
            val now = System.currentTimeMillis() / 1000
            val exp = now + (24 * 60 * 60)

            val claims = mutableMapOf<String, Any>(
                "iss" to LIVEKIT_API_KEY,
                "sub" to participantName,
                "iat" to now,
                "exp" to exp,
                "nbf" to now,
                "jti" to UUID.randomUUID().toString()
            )

            val videoGrant = mapOf(
                "room" to roomName,
                "roomJoin" to true,
                "roomAdmin" to false,
                "canPublish" to true,
                "canSubscribe" to true,
                "canPublishData" to true
            )

            claims["video"] = videoGrant
            claims["name"] = participantName

            Log.d("CallManager", "Token claims: $claims")

            val token = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, LIVEKIT_SECRET_KEY.toByteArray())
                .compact()

            Log.d("CallManager", "Generated token for $participantName in room $roomName")
            return token

        } catch (e: Exception) {
            Log.e("CallManager", "Error generating token", e)
            throw RuntimeException("Failed to generate access token: ${e.message}")
        }
    }

    private fun listenForParticipantJoin(roomId: String) {
        try {
            database.child("calls").child(roomId).child("participants")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val participantsList = mutableListOf<String>()
                            snapshot.children.forEach { child ->
                                child.getValue(String::class.java)?.let { participantsList.add(it) }
                            }

                            Log.d("CallManager", "Participants in room $roomId: $participantsList")

                            if (participantsList.size >= 2) {
                                database.child("calls").child(roomId).child("status").setValue("active")
                                Log.d("CallManager", "Call is now active with ${participantsList.size} participants")
                            }
                        } catch (e: Exception) {
                            Log.e("CallManager", "Error processing participants", e)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("CallManager", "Error listening for participants: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e("CallManager", "Error setting up participant listener", e)
        }
    }
    fun joinExistingRoom(
        roomId: String,
        userId: String,
        onTokenReceived: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            Log.d("CallManager", "Joining existing room: $roomId for user: $userId")

            val callRoomRef = database.child("calls").child(roomId)
            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null && callRoom.status != "ended") {
                            var token = callRoom.tokens[userId]
                            if (token == null) {
                                Log.d("CallManager", "Generating new token for user: $userId")
                                token = generateToken(roomId, userId)
                                val updatedTokens = callRoom.tokens.toMutableMap()
                                updatedTokens[userId] = token

                                callRoomRef.child("tokens").setValue(updatedTokens)
                                    .addOnSuccessListener {
                                        Log.d("CallManager", "Token added for user: $userId")
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.e("CallManager", "Failed to add token", exception)
                                    }
                            }
                            val updatedParticipants = callRoom.participants.toMutableList()
                            if (!updatedParticipants.contains(userId)) {
                                updatedParticipants.add(userId)

                                val updates = mapOf(
                                    "participants" to updatedParticipants,
                                    "status" to if (updatedParticipants.size >= 2) "active" else "waiting"
                                )

                                callRoomRef.updateChildren(updates)
                                    .addOnSuccessListener {
                                        Log.d("CallManager", "Successfully updated room participants")
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.e("CallManager", "Failed to update participants", exception)
                                    }
                            }

                            onTokenReceived(token)

                        } else {
                            Log.e("CallManager", "Room not found or ended: $roomId")
                            onError("Room not available or has ended")
                        }
                    } catch (e: Exception) {
                        Log.e("CallManager", "Error processing room data", e)
                        onError("Error accessing room: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CallManager", "Database error: ${error.message}")
                    onError("Database error: ${error.message}")
                }
            })

        } catch (e: Exception) {
            Log.e("CallManager", "Error in joinExistingRoom", e)
            onError("Failed to join room: ${e.message}")
        }
    }

}
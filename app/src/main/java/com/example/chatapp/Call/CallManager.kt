package com.example.chatapp.Call

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import okhttp3.OkHttpClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class CallManager {
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    private val LIVEKIT_API_KEY = "APIYJKVwDFd2gTv"
    private val LIVEKIT_SECRET_KEY = "RIOnqBjnnKfBllyuKUKzDhBeZU4KV84laKf9FSIwXO2B"
    private val NOTIFICATION_SERVER_URL = "http://192.168.153.145:3000/message"

    fun initiateCall(
        senderId: String?,
        receiverId: String?,
        onRoomCreated: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Validate input parameters
        if (senderId.isNullOrEmpty()) {
            onError("Invalid sender ID")
            return
        }
        if (receiverId.isNullOrEmpty()) {
            onError("Invalid receiver ID")
            return
        }

        try {
            val roomId = generateConsistentRoomName(senderId, receiverId)
            if (roomId.isNullOrEmpty()) {
                onError("Failed to generate room ID")
                return
            }

            val callRoomRef = database.child("calls").child(roomId)
            Log.d("CallManager", "Initiating call - Room: $roomId, Sender: $senderId, Receiver: $receiverId")

            // Check if there's an active call between these users
            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val callRoom = snapshot.getValue(CallRoom::class.java)
                            if (callRoom != null && !callRoom.roomId.isNullOrEmpty()) {
                                when (callRoom.status) {
                                    "active" -> {
                                        Log.d("CallManager", "Call is already active, joining...")
                                        joinExistingRoom(roomId, senderId, onRoomCreated, onError)
                                    }
                                    "waiting" -> {
                                        Log.d("CallManager", "Call is waiting, joining...")
                                        joinExistingRoom(roomId, senderId, onRoomCreated, onError)
                                    }
                                    "ended" -> {
                                        Log.d("CallManager", "Previous call ended, creating new one...")
                                        createNewRoom(roomId, senderId, receiverId, onRoomCreated, onError)
                                    }
                                    else -> {
                                        Log.d("CallManager", "Creating new room...")
                                        createNewRoom(roomId, senderId, receiverId, onRoomCreated, onError)
                                    }
                                }
                            } else {
                                Log.d("CallManager", "Invalid room data, creating new...")
                                createNewRoom(roomId, senderId, receiverId, onRoomCreated, onError)
                            }
                        } else {
                            Log.d("CallManager", "No existing room, creating new...")
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

        } catch (e: Exception) {
            Log.e("CallManager", "Error initiating call", e)
            onError("Failed to initiate call: ${e.message}")
        }
    }
        fun declineCall(roomId: String?, userId: String?) {
            if (roomId.isNullOrEmpty() || userId.isNullOrEmpty()) {
                Log.e("CallManager", "Invalid room ID or user ID for decline")
                return
            }

            try {
                Log.d("CallManager", "Declining call - Room: $roomId, User: $userId")

                // Get the call room to find the sender
                database.child("calls").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null && !callRoom.createdBy.isNullOrEmpty()) {
                            val senderId = callRoom.createdBy

                            // End the call
                            endCall(roomId, userId)

                            // Send decline notification to caller if it's not the same user
                            if (senderId != userId) {
                                sendDeclineNotification(senderId, roomId)
                            }
                        } else {
                            Log.e("CallManager", "Invalid call room data")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("CallManager", "Error getting call data: ${error.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("CallManager", "Error in declineCall", e)
            }
        }


        fun fetchJoinTokenFromServer(
        roomId: String?,
        userId: String?,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (roomId.isNullOrEmpty()) {
            onFailure("Invalid room ID")
            return
        }
        if (userId.isNullOrEmpty()) {
            onFailure("Invalid user ID")
            return
        }

        val url = "http://127.0.0.1:3000/get-token"

        val json = """
        {
            "roomId": "$roomId",
            "userId": "$userId"
        }
    """.trimIndent()

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure("Network Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        try {
                            val token = JSONObject(responseBody).optString("token", "")
                            if (token.isNotEmpty()) {
                                onSuccess(token)
                            } else {
                                onFailure("Token not found in response")
                            }
                        } catch (e: Exception) {
                            onFailure("Invalid JSON response: ${e.message}")
                        }
                    } else {
                        onFailure("Empty response body")
                    }
                } else {
                    onFailure("Server Error: ${response.code}")
                }
            }
        })
    }

    private fun sendCallNotification(
        senderId: String,
        receiverId: String,
        roomId: String,
        callType: String
    ) {
        if (senderId.isEmpty() || receiverId.isEmpty() || roomId.isEmpty()) {
            Log.e("CallManager", "Invalid parameters for notification")
            return
        }

        Log.d("CallManager", "Preparing to send notification from $senderId to $receiverId")

        // Get sender's name and receiver's FCM token
        firestore.collection("Users").document(senderId)
            .get()
            .addOnSuccessListener { senderDoc ->
                val senderName = senderDoc.getString("name")
                    ?: senderDoc.getString("email")
                    ?: senderId
                Log.d("CallManager", "Sender name: $senderName")

                firestore.collection("Users").document(receiverId)
                    .get()
                    .addOnSuccessListener { receiverDoc ->
                        val fcmToken = receiverDoc.getString("fcmToken")
                        Log.d("CallManager", "Receiver FCM token exists: ${fcmToken != null}")

                        if (!fcmToken.isNullOrEmpty()) {
                            Log.d("CallManager", "Sending notification to $receiverId with token: ${fcmToken.take(20)}...")
                            sendNotificationViaHTTP(fcmToken, senderName, callType, senderId, roomId,receiverId)
                        } else {
                            Log.w("CallManager", "No FCM token found for receiver: $receiverId")
                            Log.d("CallManager", "Available fields in receiver doc: ${receiverDoc.data?.keys}")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CallManager", "Failed to get receiver data for $receiverId", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CallManager", "Failed to get sender data for $senderId", e)
            }
    }

    private fun sendNotificationViaHTTP(
        fcmToken: String,
        senderName: String,
        callType: String,
        senderId: String,
        roomId: String,
        receiverId: String
    ) {
        if (fcmToken.isEmpty() || senderName.isEmpty() || senderId.isEmpty() || roomId.isEmpty()) {
            Log.e("CallManager", "Invalid parameters for HTTP notification")
            return
        }

        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("token", fcmToken)
            put("title", "Incoming Call")
            put("body", "$senderName is calling...")
            put("data", JSONObject().apply {
                put("call_type", callType)
                put("sender_id", senderId)
                put("sender_name", senderName)
                put("room_id", roomId)
                put("receiver_id",receiverId)
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(NOTIFICATION_SERVER_URL)
            .post(requestBody)
            .build()

        Log.d("CallManager", "Sending HTTP notification with payload: ${json.toString()}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CallManager", "Failed to send notification via HTTP", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("CallManager", "HTTP notification sent successfully")
                } else {
                    Log.e("CallManager", "HTTP notification failed: ${response.code} - ${response.message}")
                    Log.e("CallManager", "Response body: ${response.body?.string()}")
                }
                response.close()
            }
        })
    }


    private fun sendDeclineNotification(senderId: String, roomId: String) {
        if (senderId.isEmpty() || roomId.isEmpty()) {
            Log.e("CallManager", "Invalid parameters for decline notification")
            return
        }

        firestore.collection("Users").document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                if (!fcmToken.isNullOrEmpty()) {
                    val client = OkHttpClient()

                    val json = JSONObject().apply {
                        put("token", fcmToken)
                        put("title", "Call Declined")
                        put("body", "Your call was declined")
                        put("data", JSONObject().apply {
                            put("call_type", "call_declined")
                            put("room_id", roomId)
                        })
                    }

                    val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url(NOTIFICATION_SERVER_URL)
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e("CallManager", "Failed to send decline notification", e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                Log.d("CallManager", "Decline notification sent successfully")
                            } else {
                                Log.e("CallManager", "Failed to send decline notification: ${response.code}")
                            }
                            response.close()
                        }
                    })
                } else {
                    Log.w("CallManager", "No FCM token found for sender: $senderId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CallManager", "Failed to get sender data: $senderId", e)
            }
    }

    fun acceptCall(roomId: String?,
                   userId: String?,
                   onTokenReceived: (roomId: String, token: String) -> Unit,
                   onError: (String) -> Unit) {
        if (roomId.isNullOrEmpty() || userId.isNullOrEmpty()) {
            onError("Invalid room ID or user ID")
            return
        }

        try {
            Log.d("CallManager", "Accepting call - Room: $roomId, User: $userId")

            // Send notification to caller first
            database.child("calls").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val callRoom = snapshot.getValue(CallRoom::class.java)
                    if (callRoom != null && !callRoom.createdBy.isNullOrEmpty()) {
                        val callerId = callRoom.createdBy
                        if (callerId != userId) {
                            sendAcceptNotification(callerId, roomId)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CallManager", "Error getting call data: ${error.message}")
                }
            })

            // Join the existing room
            joinExistingRoom(roomId, userId, onTokenReceived, onError)

        } catch (e: Exception) {
            Log.e("CallManager", "Error in acceptCall", e)
            onError("Failed to accept call: ${e.message}")
        }
    }

    private fun sendAcceptNotification(callerId: String, roomId: String) {
        if (callerId.isEmpty() || roomId.isEmpty()) {
            Log.e("CallManager", "Invalid parameters for accept notification")
            return
        }

        firestore.collection("Users").document(callerId)
            .get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                if (!fcmToken.isNullOrEmpty()) {
                    val client = OkHttpClient()

                    val json = JSONObject().apply {
                        put("token", fcmToken)
                        put("title", "Call Accepted")
                        put("body", "Your call was accepted")
                        put("data", JSONObject().apply {
                            put("call_type", "call_accepted")
                            put("room_id", roomId)
                        })
                    }

                    val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url(NOTIFICATION_SERVER_URL)
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e("CallManager", "Failed to send accept notification", e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                Log.d("CallManager", "Accept notification sent successfully")
                            } else {
                                Log.e("CallManager", "Failed to send accept notification: ${response.code}")
                            }
                            response.close()
                        }
                    })
                } else {
                    Log.w("CallManager", "No FCM token found for caller: $callerId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CallManager", "Failed to get caller data: $callerId", e)
            }
    }

    private fun createNewRoom(
        roomId: String,
        creatorId: String,
        receiverId: String,
        onRoomCreated: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (roomId.isEmpty() || creatorId.isEmpty() || receiverId.isEmpty()) {
            onError("Invalid parameters for room creation")
            return
        }

        try {
            Log.d("CallManager", "Creating new room: $roomId")
            Log.d("CallManager", "Creator: $creatorId, Receiver: $receiverId")

            val creatorToken = generateToken(roomId, creatorId)
            val receiverToken = generateToken(roomId, receiverId)

            if (creatorToken.isNullOrEmpty() || receiverToken.isNullOrEmpty()) {
                onError("Failed to generate tokens")
                return
            }

            Log.d("CallManager", "Tokens generated successfully")

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
                    Log.d("CallManager", "Room created successfully in database")

                    // Send notification to receiver
                    sendCallNotification(
                        senderId = creatorId,
                        receiverId = receiverId,
                        roomId = roomId,
                        callType = "incoming_call"
                    )

                    // Return the token to the creator
                    onRoomCreated(roomId, creatorToken)

                    // Listen for participant joins
                    listenForParticipantJoin(roomId)
                }
                .addOnFailureListener { exception ->
                    Log.e("CallManager", "Failed to create room in database", exception)
                    onError("Failed to create room: ${exception.message}")
                }

        } catch (e: Exception) {
            Log.e("CallManager", "Error creating room", e)
            onError("Token generation failed: ${e.message}")
        }
    }

    fun joinExistingRoom(
        roomId: String,
        userId: String,
        onRoomJoined: (roomId: String, token: String) -> Unit,
        onError: (String) -> Unit

    ) {

        if (roomId.isEmpty() || userId.isEmpty()) {
            onError("Invalid room ID or user ID")
            return
        }

        try {
            Log.d("CallManager", "Joining existing room: $roomId for user: $userId")

            val callRoomRef = database.child("calls").child(roomId)

            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null && !callRoom.status.equals("ended", true)) {
                            Log.d("CallManager", "Room exists with status: ${callRoom.status}")

                            var token = callRoom.tokens[userId]

                            // Generate token if not exists
                            if (token.isNullOrEmpty()) {
                                Log.d("CallManager", "Generating new token for user: $userId")
                                token = generateToken(roomId, userId)
                                if (token.isNullOrEmpty()) {
                                    onError("Failed to generate token")
                                    return
                                }
                                val updatedTokens = callRoom.tokens.toMutableMap()
                                updatedTokens[userId] = token

                                callRoomRef.child("tokens").setValue(updatedTokens)
                                    .addOnFailureListener { exception ->
                                        Log.e("CallManager", "Failed to add token", exception)
                                    }
                            }

                            val updatedParticipants = callRoom.participants.toMutableList()
                            if (!updatedParticipants.contains(userId)) {
                                updatedParticipants.add(userId)
                                Log.d("CallManager", "Adding user to participants: $userId")
                            }

                            val newStatus = if (updatedParticipants.size >= 2) "active" else "waiting"

                            val updates = mapOf(
                                "participants" to updatedParticipants,
                                "status" to newStatus
                            )

                            callRoomRef.updateChildren(updates)
                                .addOnSuccessListener {
                                    Log.d("CallManager", "Successfully joined room. Status: $newStatus")
                                    if (!token.isNullOrEmpty()) {
                                        onRoomJoined(roomId, token)
                                    } else {
                                        Log.e("CallManager", "Token is null after generation")
                                        onError("Failed to get valid token")
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Log.e("CallManager", "Failed to update room", exception)
                                    onError("Failed to join room: ${exception.message}")
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

    private fun generateConsistentRoomName(senderId: String, receiverId: String): String? {
        if (senderId.isEmpty() || receiverId.isEmpty()) {
            Log.e("CallManager", "Invalid sender or receiver ID")
            return null
        }

        val participants = listOf(senderId, receiverId).sorted()
        val roomName = "call_${participants.joinToString("_")}"
        Log.d("CallManager", "Generated room name: $roomName for participants: $participants")
        return roomName
    }

    fun endCall(roomId: String?, userId: String?) {
        if (roomId.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Log.e("CallManager", "Invalid room ID or user ID for end call")
            return
        }

        try {
            Log.d("CallManager", "Ending call - Room: $roomId, User: $userId")

            val callRoomRef = database.child("calls").child(roomId)

            callRoomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val callRoom = snapshot.getValue(CallRoom::class.java)
                        if (callRoom != null) {
                            val updatedParticipants = callRoom.participants.toMutableList()
                            updatedParticipants.remove(userId)

                            if (updatedParticipants.isEmpty()) {
                                // No participants left, end the call
                                val updates = mapOf(
                                    "status" to "ended",
                                    "endedAt" to System.currentTimeMillis(),
                                    "participants" to emptyList<String>()
                                )
                                callRoomRef.updateChildren(updates)
                                    .addOnSuccessListener {
                                        Log.d("CallManager", "Room marked as ended: $roomId")
                                        val receiverId = callRoom.participants.firstOrNull { it != userId }
                                        if (receiverId != null) {
                                            sendCallEndedNotification(roomId, receiverId, userId, callRoom.participants)
                                        } else {
                                            Log.e("CallManager", "No valid receiverId found to send call ended notification")
                                        }

                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            callRoomRef.removeValue()
                                        }, 10000) // 10 seconds delay
                                    }
                            } else {
                                // Update participants
                                val updates = mapOf(
                                    "participants" to updatedParticipants,
                                    "status" to if (updatedParticipants.size == 1) "waiting" else "active"
                                )
                                callRoomRef.updateChildren(updates)
                                Log.d("CallManager", "User left room: $roomId, remaining: ${updatedParticipants.size}")
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

    private fun sendCallEndedNotification(roomId: String,  receiverId:String, userId: String, originalParticipants: List<String>) {
        if (roomId.isEmpty() || userId.isEmpty()) {
            Log.e("CallManager", "Invalid parameters for call ended notification")
            return
        }

        originalParticipants.forEach { participantId ->
            if (participantId != userId && participantId.isNotEmpty()) {
                firestore.collection("Users").document(participantId)
                    .get()
                    .addOnSuccessListener { document ->
                        val fcmToken = document.getString("fcmToken")
                        if (!fcmToken.isNullOrEmpty()) {
                            val client = OkHttpClient()

                            val json = JSONObject().apply {
                                put("token", fcmToken)
                                put("title", "Call Ended")
                                put("body", "The call has ended")
                                put("data", JSONObject().apply {
                                    put("call_type", "call_ended")
                                    put("room_id", roomId)
                                    put("receiver_id",receiverId)
                                })
                            }

                            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                            val request = Request.Builder()
                                .url(NOTIFICATION_SERVER_URL)
                                .post(requestBody)
                                .build()

                            client.newCall(request).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    Log.e("CallManager", "Failed to send call ended notification", e)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    if (response.isSuccessful) {
                                        Log.d("CallManager", "Call ended notification sent to: $participantId")
                                    } else {
                                        Log.e("CallManager", "Failed to send call ended notification: ${response.code}")
                                    }
                                    response.close()
                                }
                            })
                        }
                    }
            }
        }
    }

    private fun generateToken(roomName: String, participantName: String): String? {
        if (roomName.isEmpty() || participantName.isEmpty()) {
            Log.e("CallManager", "Invalid room name or participant name")
            return null
        }

        return try {
            val now = System.currentTimeMillis() / 1000
            val exp = now + (24 * 60 * 60) // 24 hours

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

            val token = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, LIVEKIT_SECRET_KEY.toByteArray())
                .compact()

            Log.d("CallManager", "Generated token for $participantName in room $roomName")
            token

        } catch (e: Exception) {
            Log.e("CallManager", "Error generating token", e)
            null
        }
    }

    private fun listenForParticipantJoin(roomId: String) {
        if (roomId.isEmpty()) {
            Log.e("CallManager", "Invalid room ID for participant listener")
            return
        }

        try {
            database.child("calls").child(roomId).child("participants")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val participantsList = mutableListOf<String>()
                            snapshot.children.forEach { child ->
                                child.getValue(String::class.java)?.let { participant ->
                                    if (participant.isNotEmpty()) {
                                        participantsList.add(participant)
                                    }
                                }
                            }

                            Log.d("CallManager", "Participants in room $roomId: $participantsList")

                            if (participantsList.size >= 2) {
                                database.child("calls").child(roomId).child("status")
                                    .setValue("active")
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
    }

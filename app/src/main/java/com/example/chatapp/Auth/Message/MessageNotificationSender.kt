package com.example.chatapp.CallNotification

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

object MessageNotificationSender {

    fun sendMessageNotification(
        context: Context,
        senderId: String,
        receiverId: String,
        messageType: String // "text", "image", "voice"
    ) {
        NotificationTokenManager.getTokenForUser(receiverId) { token ->
            if (token != null) {
                val url = "http://192.168.244.145:3000/message"

                val requestBody = JSONObject().apply {
                    put("token", token)
                    put("title", "New Message")
                    put("body", "Incoming $messageType message from $senderId")
                    put("data", JSONObject().apply {
                        put("senderId", senderId)
                        put("type", messageType)
                    })
                }

                val request = JsonObjectRequest(Request.Method.POST, url, requestBody,
                    { response ->
                        Log.d("FCM", "Message notification sent: $response")
                    },
                    { error ->
                        Log.e("FCM", "Message notification failed", error)
                    }
                )

                Volley.newRequestQueue(context).add(request)

            } else {
                Log.e("FCM", "No FCM token found for user: $receiverId")
            }
        }
    }
}

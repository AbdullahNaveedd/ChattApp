package com.example.chatapp.CallNotification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.chatapp.Activity.MainActivity
import com.example.chatapp.Call.CallManager
import com.google.firebase.auth.FirebaseAuth

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        val roomId = intent?.getStringExtra("room_id")
        val callType = intent?.getStringExtra("call_type")
        val senderId = intent?.getStringExtra("sender_id")
        val senderName = intent?.getStringExtra("sender_name")

        if (context == null || roomId == null || callType == null || senderId == null) {
            Log.e("CallActionReceiver", "Missing required data")
            return
        }

        Log.d("CallActionReceiver", "Action: $action, Room: $roomId, Sender: $senderId")

        // Cancel the notification first
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        when (action) {
            "ACTION_ACCEPT_CALL" -> {
                Log.d("CallActionReceiver", "Accepting call")

                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId == null) {
                    Log.e("CallActionReceiver", "Current user is null")
                    return
                }

                val acceptIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("action", "accept_call")
                    putExtra("sender_id", senderId)
                    putExtra("receiver_id", currentUserId)
                    putExtra("room_id", roomId)
                    putExtra("call_type", callType) // "voice" or "video"
                }

                context.startActivity(acceptIntent)
            }

            "ACTION_DECLINE_CALL" -> {
                Log.d("CallActionReceiver", "Declining call")

                if (currentUserId != null) {
                    val callManager = CallManager()
                    callManager.declineCall(roomId, currentUserId)
                }

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("action", "decline_call")
                    putExtra("room_id", roomId)
                    putExtra("call_type", callType)
                    putExtra("sender_id", senderId)
                    putExtra("sender_name", senderName)
                }
                context.startActivity(mainIntent)
            }
        }
    }
}
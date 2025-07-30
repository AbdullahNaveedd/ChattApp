package com.example.chatapp.CallNotification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.chatapp.Activity.MainActivity
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        NotificationTokenManager.saveToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")
        val data = remoteMessage.data

        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")

            val callType = remoteMessage.data["call_type"]
            val senderId = remoteMessage.data["sender_id"]
            val senderName = remoteMessage.data["sender_name"]
            val roomId = remoteMessage.data["room_id"]
            val receiverId = remoteMessage.data["receiver_id"]
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            Log.d("FCM", "Room id is: $roomId")
            Log.d("FCM", "Call type is: $callType")
            Log.d("FCM", "Receiver ID from FCM: $receiverId")
            Log.d("FCM", "Current device user ID: $currentUserId")

            when (callType) {
                "incoming_call" -> {
                    val isNewCall = data["isNewCall"]?.toString()?.toBoolean() ?: true
                    if (isNewCall) {
                        showIncomingCallNotification(senderId, senderName, roomId, callType)
                    } else {
                        Log.d("CallNotification", "Switch call detected. Not showing incoming call notification.")
                    }
                }
                "call_ended" -> {
                    cancelCallNotification()
                }
                "call_accepted" -> cancelCallNotification()
                "call_declined" -> cancelCallNotification()
            }
        }

        // Only show simple notification for regular messages, not for calls
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            // Check if this is not a call-related notification
            val callType = remoteMessage.data["call_type"]
            if (callType == null || callType.isEmpty()) {
                val data = remoteMessage.data

                sendNotification(
                    senderId = data["sender_id"],
                    senderName = data["sender_name"],
                    roomId = data["room_id"],
                    callType = null,
                    messageBody = remoteMessage.notification?.body ?: "New message"
                )
            }

        }
    }

    private fun isAppInForeground(): Boolean {
        // You can implement this method to check if app is in foreground
        // For now, we'll always show the call notification with actions
        return false
    }

    private fun showIncomingCallNotification(senderId: String?, senderName: String?, roomId: String?, callType: String?) {
        val channelId = "incoming_call_channel"
        val notificationId = 1001
        val context = this

        // Create intent to open the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("room_id", roomId)
            putExtra("call_type", callType)
            putExtra("sender_id", senderId)
            putExtra("sender_name", senderName)
                .putExtra("action", "incoming_call")
            putExtra("show_call_dialog", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept call intent
        val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = "ACTION_ACCEPT_CALL"
            putExtra("room_id", roomId)
            putExtra("call_type", callType)
            putExtra("sender_id", senderId)
            putExtra("sender_name", senderName)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline call intent
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = "ACTION_DECLINE_CALL"
            putExtra("room_id", roomId)
            putExtra("call_type", callType)
            putExtra("sender_id", senderId)
            putExtra("sender_name", senderName)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context, 1, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.call)
            .setContentTitle("Incoming Call")
            .setContentText("$senderName is calling...")
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .addAction(R.drawable.declinecall, "Decline", declinePendingIntent)
            .addAction(R.drawable.acceptcall, "Accept", acceptPendingIntent)
            .setContentIntent(openAppPendingIntent) // This handles the tap on notification body
            .setFullScreenIntent(acceptPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)


        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun cancelCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)
    }

    private fun sendNotification(senderId: String?, senderName: String?, roomId: String?, callType: String?,messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("call_type", callType)
            putExtra("room_id", roomId)
            putExtra("sender_id", senderId)
            putExtra("sender_name", senderName)
            putExtra("show_call_dialog", true)

            Log.d("CallDebug", "Action: incoming_call, CallType: $callType, showCallDialog: true")

        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ChatApp")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
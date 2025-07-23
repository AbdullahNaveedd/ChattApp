package com.example.chatapp.Activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.chatapp.CallNotification.NotificationTokenManager
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            100
        )

        NotificationTokenManager.initialize(this)

        if (intent?.hasExtra("action") == true) {
            handleNotificationIntent(intent)
        } else {
            handleNormalStartup()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "Audio recording permission granted")
            } else {
                Log.e("Permission", "Audio recording permission denied")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNormalStartup() {
        val sharedPref = getSharedPreferences("auth_pref", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        Handler(Looper.getMainLooper()).postDelayed({
            val target = if (isLoggedIn) Fragement_Activity::class.java else Onboarding::class.java
            startActivity(Intent(this, target))
            finish()
        }, SPLASH_DELAY)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            when (it.getStringExtra("action")) {
                "accept_call" -> handleAcceptCall(it)
                "decline_call" -> handleDeclineCall(it)
                "missed_call" -> handleMissedCall(it)
            }
        }
    }

    private fun handleAcceptCall(intent: Intent) {
        Log.d("CallFlow", "handleAcceptCall() triggered")


        val originalSenderId = intent.getStringExtra("sender_id")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val roomId = intent.getStringExtra("room_id")
        val callType = intent.getStringExtra("call_type") ?: "voice"

        Log.d("CallFlow", "roomId: $roomId, currentUserId: $currentUserId, senderId: $originalSenderId")


        Log.d("Roooms","Room id exist $roomId")
        if (roomId != null && currentUserId != null) {
            val callRef = FirebaseDatabase.getInstance().reference.child("calls").child(roomId)

            callRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val participantsList = snapshot.child("participants").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()

                    if (!participantsList.contains(currentUserId)) {
                        participantsList.add(currentUserId)
                        callRef.child("participants").setValue(participantsList)
                            .addOnSuccessListener {
                                Log.d("RealtimeDB", "Receiver added to participants.")
                                launchCallScreen(originalSenderId, currentUserId, roomId, callType)
                            }
                            .addOnFailureListener { e ->
                                Log.e("RealtimeDB", "Failed to update participants: ${e.message}")
                                launchCallScreen(originalSenderId, currentUserId, roomId, callType) // still launch fallback
                            }
                    } else {
                        launchCallScreen(originalSenderId, currentUserId, roomId, callType)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RealtimeDB", "DB error: ${error.message}")
                    launchCallScreen(originalSenderId, currentUserId, roomId, callType)
                }
            })
        } else {
            Log.e("Call", "Missing roomId or currentUserId")
        }
    }
    private fun launchCallScreen(senderId: String?, receiverId: String, roomId: String, callType: String) {
        val callIntent = Intent(this, Fragement_Activity::class.java).apply {
            putExtra("show_call_fragment", true)
            putExtra("call_fragment_type", callType)
            putExtra("sender_id", senderId)
            putExtra("receiver_id", receiverId)
            putExtra("room_id", roomId)
            putExtra("isCallInitiator", false)
        }
        startActivity(callIntent)
        finish()
    }




    private fun handleDeclineCall(intent: Intent) {
        val senderId = intent.getStringExtra("sender_id")
        val roomId = intent.getStringExtra("room_id")
        Log.d(TAG, "Declining call from $senderId")


        val intent = Intent(this, Fragement_Activity::class.java)
        startActivity(intent)
        finish()
    }

    private fun handleMissedCall(intent: Intent) {
        val senderId = intent.getStringExtra("sender_id")
        val senderName = intent.getStringExtra("sender_name")

        Log.d(TAG, "Handling missed call from $senderName")

        val intent = Intent(this, Fragement_Activity::class.java).apply {
            putExtra("show_missed_call", true)
            putExtra("missed_call_sender", senderId)
            putExtra("missed_call_sender_name", senderName)
        }
        startActivity(intent)
        finish()
    }

    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}

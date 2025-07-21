package com.example.chatapp.Activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.CallNotification.NotificationTokenManager
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        NotificationTokenManager.initialize(this)

        if (intent?.hasExtra("action") == true) {
            handleNotificationIntent(intent)
        } else {
            handleNormalStartup()
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
        val originalSenderId = intent.getStringExtra("sender_id") // Person who made the call
        val currentUserId = getCurrentUserId() // Current user accepting the call
        val roomId = intent.getStringExtra("room_id")
        val callType = intent.getStringExtra("call_type") ?: "voice"

        val callIntent = Intent(this, Fragement_Activity::class.java).apply {
            putExtra("show_call_fragment", true)
            putExtra("call_fragment_type", callType)
            putExtra("sender_id", originalSenderId)
            putExtra("receiver_id", currentUserId)
            putExtra("room_id", roomId)
            putExtra("isCallInitiator", false) // Ahmed is joining, not initiating
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

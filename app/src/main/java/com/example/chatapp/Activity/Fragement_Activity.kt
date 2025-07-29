package com.example.chatapp.Activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.Fragements.Home
import com.example.chatapp.Fragements.Videocall
import com.example.chatapp.Fragements.VoiceCall
import com.example.chatapp.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth

class Fragement_Activity : AppCompatActivity() {

    private var interstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_fragement2)
        val showIncomingCallDialog = intent.getBooleanExtra("show_incoming_call_dialog", false)
        if (showIncomingCallDialog) {
            showIncomingCallDialog()
            return
        }

        val showCallFragment = intent.getBooleanExtra("show_call_fragment", false)
        if (showCallFragment) {
            val callType = intent.getStringExtra("call_fragment_type") ?: "voice"
            val senderId = intent.getStringExtra("sender_id")
            val receiverId = intent.getStringExtra("receiver_id")
            val roomId = intent.getStringExtra("room_id")
            val isCallInitiator = intent.getBooleanExtra("isCallInitiator", false)
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            Log.d("FragmentActivity", "Call Fragment Info:")
            Log.d("FragmentActivity", "CallType: $callType")
            Log.d("FragmentActivity", "SenderId: $senderId")
            Log.d("FragmentActivity", "ReceiverId: $receiverId")
            Log.d("FragmentActivity", "RoomId: $roomId")
            Log.d("FragmentActivity", "IsCallInitiator: $isCallInitiator")
            Log.d("FragmentActivity", "CurrentUserId: $currentUserId")

            // Fix the user ID logic - determine who is the caller and receiver
            val actualSenderId: String?
            val actualReceiverId: String?

            if (isCallInitiator) {
                // Current user is initiating the call
                actualSenderId = currentUserId
                actualReceiverId = receiverId ?: senderId // Use receiverId if available, otherwise senderId
            } else {
                // Current user is receiving the call
                actualSenderId = senderId // The person who initiated the call
                actualReceiverId = currentUserId // Current user is the receiver
            }

            Log.d("FragmentActivity", "Corrected IDs:")
            Log.d("FragmentActivity", "ActualSenderId: $actualSenderId")
            Log.d("FragmentActivity", "ActualReceiverId: $actualReceiverId")

            val bundle = Bundle().apply {
                putString("senderId", actualSenderId)
                putString("receiverId", actualReceiverId)
                putString("roomId", roomId)
                putBoolean("isCallInitiator", isCallInitiator)
                putBoolean("isIncomingCall", !isCallInitiator)
                putString("callType", callType) // Add call type to bundle
            }

            val fragment = when (callType) {
//                "video" -> {
//                    Log.d("FragmentActivity", "Creating Videocall fragment")
//                    Videocall().apply { arguments = bundle }
//                }
                "voice" -> {
                    Log.d("FragmentActivity", "Creating VoiceCall fragment")
                    VoiceCall().apply { arguments = bundle }
                }
                else -> {
                    Log.w("FragmentActivity", "Unknown call type: $callType, defaulting to voice")
                    VoiceCall().apply { arguments = bundle }
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .commit()
        } else {
            // Handle missed call display
            val showMissedCall = intent.getBooleanExtra("show_missed_call", false)
            if (showMissedCall) {
                // You can handle missed call display here
                Log.d("FragmentActivity", "Showing missed call notification")
            }

            // Show default home fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, Home())
                .commit()
        }

        // Load ads
        loadInterstitialAd()
    }
    private fun showIncomingCallDialog() {
        val senderId = intent.getStringExtra("sender_id")
        val senderName = intent.getStringExtra("sender_name")
        val roomId = intent.getStringExtra("room_id")
        val callType = intent.getStringExtra("call_type") ?: "voice"

        Log.d("FragmentActivity", "Showing incoming call dialog for $senderName")

        // ✅ Create and show call dialog fragment
        val bundle = Bundle().apply {
            putString("sender_id", senderId)
            putString("sender_name", senderName)
            putString("room_id", roomId)
            putString("call_type", callType)
        }

        val dialogFragment = IncomingCall().apply {
            arguments = bundle
        }

        dialogFragment.show(supportFragmentManager, "IncomingCallDialog")

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_view, Home())
            .commit()
    }

    private fun loadInterstitialAd() {
        MobileAds.initialize(this) {}

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdLog", "Ad loaded.")
                    // Don't show ad immediately during calls
                    val showCallFragment = intent.getBooleanExtra("show_call_fragment", false)
                    if (!showCallFragment) {
                        ad.show(this@Fragement_Activity)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdLog", "Ad failed to load: ${error.message}")
                }
            })
    }
}
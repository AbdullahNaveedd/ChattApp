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

            val bundle = Bundle().apply {
                putString("senderId", senderId)
                putString("receiverId", receiverId)
                putString("roomId", roomId)
                putBoolean("isCallInitiator", isCallInitiator)
            }

            val fragment = when (callType) {
                "video" -> Videocall().apply { arguments = bundle }
                else -> VoiceCall().apply { arguments = bundle }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .commit()
        } else {
            // Show default home fragment if no call fragment is requested
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, Home())
                .commit()
        }

        MobileAds.initialize(this) {}

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdLog", "Ad loaded.")
                    ad.show(this@Fragement_Activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdLog", "Ad failed to load: ${error.message}")
                }
            })
    }
}
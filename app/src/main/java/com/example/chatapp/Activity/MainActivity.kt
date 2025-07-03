package com.example.chatapp.Activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        val sharedPref = getSharedPreferences("auth_pref", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isLoggedIn) {
                startActivity(Intent(this, Fragement_Activity::class.java))
            } else {
                startActivity(Intent(this, Onboarding::class.java))
            }
            finish()
        }, 3000)

    }
    }

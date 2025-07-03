package com.example.chatapp.Activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.R
import com.example.chatapp.login.Signup
import com.example.chatapp.login.Login
import com.google.android.gms.ads.*

class Onboarding : AppCompatActivity() {

    private lateinit var login: TextView
    private lateinit var signup: Button
    private lateinit var adView: AdView
    private val TAG = "BannerAd"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_onboarding)

        login = findViewById(R.id.Login)
        signup = findViewById(R.id.btnsigniup)
        adView = findViewById(R.id.adView)

        login.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }

        signup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
        }

        initializeBannerAd()
    }

    private fun initializeBannerAd() {
        MobileAds.initialize(this) { Log.d(TAG, "AdMob initialized") }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("AdMob", "Device ID: $deviceId")

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR, deviceId)).build()
        )

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("BannerAdd", "Ad failed to load: ${adError}")
            }

            override fun onAdLoaded() {
                Log.d("BannerAdd", "Ad successfully loaded.")
            }
        }
    }
}

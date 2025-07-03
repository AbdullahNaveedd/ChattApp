package com.example.chatapp.Activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.Fragements.Home
import com.example.chatapp.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class Fragement_Activity : AppCompatActivity() {

    private var interstitialAd: InterstitialAd? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_fragement2)

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


        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container_view, Home())
                .commit()
        }

    }
}

package com.example.chatapp.Activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.Fragements.Home
import com.example.chatapp.R


class Fragement_Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_fragement2)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container_view, Home())
                .commit()
        }

    }
}
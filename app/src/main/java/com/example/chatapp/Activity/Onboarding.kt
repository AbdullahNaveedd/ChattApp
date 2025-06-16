package com.example.chatapp.Activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.R
import com.example.chatapp.login.Signup
import com.example.chatapp.login.Login

class Onboarding : AppCompatActivity() {
    private lateinit var login:TextView
    private lateinit var signup: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_onboarding)

        login= findViewById(R.id.Login)
        signup= findViewById(R.id.btnsigniup)

        login.setOnClickListener{
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }

        signup.setOnClickListener{
            val intent = Intent(this, Signup::class.java)
            startActivity(intent)
        }

    }
}
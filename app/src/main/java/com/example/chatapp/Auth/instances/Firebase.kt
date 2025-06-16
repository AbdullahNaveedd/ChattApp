package com.example.chatapp.Auth.instances

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object Firebase {

    val auth by lazy {
        FirebaseAuth.getInstance()
    }

    val db by lazy {
        FirebaseFirestore.getInstance()
    }
}
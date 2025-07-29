package com.example.chatapp.CallNotification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object NotificationTokenManager {
    private const val PREFS_NAME = "notification_prefs"
    private const val TOKEN_KEY = "fcm_token"

    private lateinit var prefs: SharedPreferences
    private val firestore = FirebaseFirestore.getInstance()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM", "FCM Registration Token: $token")
            saveToken(token)
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }
    fun getTokenForUser(userId: String, callback: (String?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val token = documentSnapshot.getString("fcmToken")
                callback(token)
            }
            .addOnFailureListener { exception ->
                Log.e("FCM", "Failed to get FCM token for user: $userId", exception)
                callback(null)
            }
    }


    fun saveTokenToFirestore(userId: String, token: String) {
        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    firestore.collection("Users").document(userId)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("FCM", "Token updated to Firestore")
                        }
                        .addOnFailureListener { e ->
                            Log.w("FCM", "Error updating token", e)
                        }
                } else {
                    val userData = hashMapOf(
                        "fcmToken" to token
                    )
                    firestore.collection("Users").document(userId)
                        .set(userData)
                        .addOnSuccessListener {
                            Log.d("FCM", "Token saved to new Firestore user document")
                        }
                        .addOnFailureListener { e ->
                            Log.w("FCM", "Error creating user document", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w("FCM", "Error checking if user exists", e)
            }


    }
}
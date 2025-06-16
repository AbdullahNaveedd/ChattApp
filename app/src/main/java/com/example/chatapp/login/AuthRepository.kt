package com.example.chatapp.login

import android.util.Log
import com.example.chatapp.Auth.instances.Firebase.auth
import com.example.chatapp.Auth.instances.Firebase.db
import com.example.chatapp.Firebase.Users
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthRepository {

    fun registerUser(name: String, email: String, password: String): Flow<Result<String>> = flow {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("User ID is null")

            Log.d("AuthRepo", "User created in Firebase Auth: $userId")

            val user = Users(
                uid = userId,
                name = name,
                email = email,
                profilePicture = "",
            )

            db.collection("Users")
                .document(userId)
                .set(user)
                .await()

            emit(Result.success("Successfully Registered"))
            Log.d("AuthRepo", "User added to Firestore: $userId")

            emit(Result.success("Successfully Registered"))
        } catch (e: Exception) {
            Log.e("AuthRepo", "Registration error: ${e.message}")
            emit(Result.failure(e))
        }
    }
    fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun loginUser(email: String, password: String): Flow<Result<String>> = flow {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                emit(Result.success("Login successful!"))
            } else {
                emit(Result.failure(Exception("User not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}

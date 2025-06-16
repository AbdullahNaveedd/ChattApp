package com.example.chatapp.Fragements

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.Auth.Message.MessageDataClass
import com.example.chatapp.Auth.Message.MessageAdapter
import com.example.chatapp.Auth.Message.Status
import com.example.chatapp.Auth.Message.StatusAdapter
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Message : Fragment() {

    private lateinit var statusRecyclerView: RecyclerView
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<MessageDataClass>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String? = null
    private var chatListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupStatusRecyclerView()
        setupMessageRecyclerView()
        initializeCurrentUser()
    }

    private fun initializeViews(view: View) {
        statusRecyclerView = view.findViewById(R.id.statusRecyclerView)
        messageRecyclerView = view.findViewById(R.id.messagerecyclerview)
    }

    private fun setupStatusRecyclerView() {
        statusRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val statusList = listOf(
            Status("Emma", R.drawable.profile),
            Status("Liam", R.drawable.profile),
            Status("Olivia", R.drawable.profile),
            Status("John", R.drawable.profile),
            Status("Emma", R.drawable.profile),
            Status("Liam", R.drawable.profile),
            Status("Olivia", R.drawable.profile)
        )
        statusRecyclerView.adapter = StatusAdapter(statusList)
    }

    private fun setupMessageRecyclerView() {
        messageRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messageAdapter = MessageAdapter(messageList) { messageData ->
            navigateToUserChat(messageData)
        }
        messageRecyclerView.adapter = messageAdapter
    }

    private fun initializeCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            currentUserId = firebaseUser.uid
            Log.d("Message", "Current user initialized: $currentUserId")
            loadUserChats()
        }
        else {
            Log.e("Message", "No authenticated user found")
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatListener?.remove()
        chatListener = null
    }

    private fun loadUserChats() {
        if (currentUserId == null) {
            Log.w("Message", "Cannot load chats - user ID is null")
            return
        }

        if (!isAdded || context == null) {
            Log.w("Message", "Fragment not attached, cannot load chats")
            return
        }

        Log.d("Message", "Loading chats for user: $currentUserId")
        chatListener =
            db.collection("Chats")
            .whereArrayContains("participants", currentUserId!!)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)

            .addSnapshotListener { snapshot, error ->
                if (!isAdded || context == null) {
                    Log.w("Message", "Fragment not attached when chats received")
                    return@addSnapshotListener
                }

                if (error != null) {
                    Log.e("Message", "Error loading chats", error)
                    Toast.makeText(requireContext(), "Error loading chats: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                messageList.clear()
                snapshot?.documents?.forEach { chatDoc ->
                    try {
                        val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
                        val lastMessage = chatDoc.getString("lastMessage") ?: ""
                        val lastMessageType = chatDoc.getString("lastMessageType") ?: ""
                        val lastMessageAt = chatDoc.getTimestamp("lastMessageAt")
                        val otherUserId = participants.find { it != currentUserId }


                        if (otherUserId != null) {
                            getUserDetails(otherUserId) { userName, userImage ->
                                if (!isAdded || context == null) {
                                    return@getUserDetails
                                }

                                val timeFormatted = if (lastMessageAt != null) {
                                    formatTimestamp(lastMessageAt.toDate())
                                } else {
                                    ""
                                }
                                val displayMessage = when (lastMessageType) {
                                    "IMAGE" -> "📷 Photo"
                                    "VOICE" -> "🎤 Voice Message"
                                    "TEXT" -> lastMessage
                                    else -> "[Unknown message]"
                                }
                                val mediaUrl = chatDoc.getString("mediaUrl") ?: ""
                                val isReadStatusMap = chatDoc.get("isReadStatus") as? Map<String, Boolean> ?: emptyMap()
                                val isRead = isReadStatusMap[currentUserId] ?: true

                                val messageData = MessageDataClass(
                                    name = userName,
                                    description = displayMessage,
                                    time = timeFormatted,
                                    imageResId = userImage,
                                    receiverId = otherUserId,
                                    mediaUrl = mediaUrl,
                                    messageType = lastMessageType,
                                    rawTime = lastMessageAt?.toDate(),
                                    isRead = isRead
                                )


                                val existingIndex = messageList.indexOfFirst { it.receiverId == otherUserId }
                                if (existingIndex >= 0) {
                                    messageList[existingIndex] = messageData
                                } else {
                                    messageList.add(messageData)
                                }

                                messageList.sortByDescending { it.rawTime }

                                if (isAdded && context != null) {
                                    messageAdapter.notifyDataSetChanged()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Message", "Error processing chat document", e)
                    }
                }
            }
    }

    private fun getUserDetails(userId: String, callback: (String, String) -> Unit) {
        if (!isAdded || context == null) {
            Log.w("Message", "Fragment not attached, skipping user details fetch")
            return
        }

        db.collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (!isAdded || context == null) {
                    Log.w("Message", "Fragment not attached when user details received")
                    return@addOnSuccessListener
                }
                val userName = userDoc.getString("name") ?: "Unknown User"
                val userImage = userDoc.getString("imageUrl") ?: ""
                callback(userName, userImage)
            }
            .addOnFailureListener { exception ->
                if (isAdded && context != null) {
                    Log.e("Message", "Error getting user details for $userId", exception)
                    callback("Unknown User", "")
                }
            }
    }

    private fun formatTimestamp(date: Date): String {
        val now = Date()
        val diffInMillis = now.time - date.time
        val diffInHours = diffInMillis / (1000 * 60 * 60)
        val diffInDays = diffInHours / 24

        return when {
            diffInHours < 1 -> {
                val diffInMinutes = diffInMillis / (1000 * 60)
                if (diffInMinutes < 1) "Just now" else "${diffInMinutes}m ago"
            }
            diffInHours < 24 -> {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            }
            diffInDays < 7 -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            }
            else -> {
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
            }
        }
    }

    private fun navigateToUserChat(messageData: MessageDataClass) {
        if (!isAdded || context == null) {
            Log.w("Message", "Fragment not attached, cannot navigate")
            return
        }

        val receiverId = messageData.receiverId
        if (receiverId != null) {
            db.collection("Chats")
                .whereArrayContains("participants", currentUserId!!)
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot?.documents?.forEach { chatDoc ->
                        val participants = chatDoc.get("participants") as? List<String> ?: return@forEach
                        val isReadStatusMap = chatDoc.get("isReadStatus") as? Map<String, Boolean> ?: emptyMap()

                        if (participants.contains(receiverId)) {
                            val isAlreadyRead = isReadStatusMap[currentUserId] ?: false

                            if (!isAlreadyRead) {
                                chatDoc.reference.update("isReadStatus.$currentUserId", true)

                            }


                            val userChatFragment = UserChat.newInstance(
                                messageData.name,
                                messageData.imageResId,
                                receiverId
                            )

                            try {
                                val fragmentTransaction = (requireActivity() as AppCompatActivity)
                                    .supportFragmentManager.beginTransaction()
                                fragmentTransaction.replace(R.id.fragment_container_view, userChatFragment)
                                fragmentTransaction.addToBackStack(null)
                                fragmentTransaction.commit()
                            } catch (e: Exception) {
                                Log.e("Message", "Error navigating to UserChat", e)
                                Toast.makeText(requireContext(), "Error opening chat", Toast.LENGTH_SHORT).show()
                            }

                            return@forEach
                        }
                    }
                }
        } else {
            Toast.makeText(requireContext(), "Invalid chat", Toast.LENGTH_SHORT).show()
        }
    }
}
package com.example.chatapp.Fragements

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chatapp.Auth.Message.MessageType
import com.example.chatapp.Auth.Message.UserChatAdapter
import com.example.chatapp.Auth.Message.UserChatDataClass
import com.example.chatapp.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserChat : Fragment() {

    private lateinit var backbtn: ImageView
    private lateinit var videobtn: ImageView
    private lateinit var voicebtn: ImageView
    private lateinit var callname: TextView
    private lateinit var callimage: ImageView
    private lateinit var recyclerViews: RecyclerView
    private lateinit var editText: TextInputEditText

    val userChatList = mutableListOf<UserChatDataClass>()
    private lateinit var adapter: UserChatAdapter
    private lateinit var camLauncher: ActivityResultLauncher<Intent>
    private var mediaUrl: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isRecording = false

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String? = null
    private var currentChatId: String? = null
    private var currentReceiverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        setupCameraLauncher()

        initializeCurrentUser()
    }

    private fun initializeCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            currentUserId = firebaseUser.uid
            Log.d("UserChat", "Current user initialized: $currentUserId")
            initializeChat()
        } else {
            Log.e("UserChat", "No authenticated user found")
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
            navigateToFragment(Home())
        }
    }

    private fun initializeViews(view: View) {
        backbtn = view.findViewById(R.id.backbtn)
        videobtn = view.findViewById(R.id.videcall)
        voicebtn = view.findViewById(R.id.voicecall)
        callname = view.findViewById(R.id.call_name)
        callimage = view.findViewById(R.id.call_image)
        editText = view.findViewById(R.id.edittextmsg)
        recyclerViews = view.findViewById(R.id.userchatrecyclerview)

        val receiverName = arguments?.getString("receiverName") ?: "Unknown"
        callname.text = receiverName

        val receiverImage = arguments?.getString("receiverImage") ?: ""
        Glide.with(requireContext())
            .load(receiverImage)
            .circleCrop()
            .placeholder(R.drawable.profile)
            .into(callimage)

        currentReceiverId = arguments?.getString("receiverId")
    }

    private fun setupRecyclerView() {
        recyclerViews.layoutManager = LinearLayoutManager(requireContext())
        adapter = UserChatAdapter(userChatList) { isVoiceCall -> }
        recyclerViews.adapter = adapter
    }

    private fun setupClickListeners() {
        val sendBtn = view?.findViewById<ImageView>(R.id.sendbtn)
        val mic = view?.findViewById<ImageView>(R.id.mic)
        val cam = view?.findViewById<ImageView>(R.id.cam)

        videobtn.setOnClickListener {
            navigateToFragment(Videocall())
        }

        voicebtn.setOnClickListener {
            val fragment = VoiceCall().apply {
                arguments = Bundle().apply {
                    putString("senderId", currentUserId)
                    putString("receiverId", currentReceiverId)
                }
            }
            navigateToFragment(fragment)
        }

        backbtn.setOnClickListener {
            navigateToFragment(Home())
        }

        sendBtn?.setOnClickListener {
            sendTextMessage()
        }

        cam?.setOnClickListener {
            openImagePicker()
        }

        mic?.setOnClickListener {
            handleVoiceRecording()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonVisibility(s.isNullOrBlank())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun navigateToFragment(fragment: Fragment) {
        val fragmentTransaction = (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container_view, fragment)
        fragmentTransaction.commit()
    }

    private fun updateButtonVisibility(isEmpty: Boolean) {
        val sendBtn = view?.findViewById<ImageView>(R.id.sendbtn)
        val mic = view?.findViewById<ImageView>(R.id.mic)
        val cam = view?.findViewById<ImageView>(R.id.cam)

        if (isEmpty) {
            sendBtn?.visibility = View.GONE
            mic?.visibility = View.VISIBLE
            cam?.visibility = View.VISIBLE
        } else {
            sendBtn?.visibility = View.VISIBLE
            mic?.visibility = View.GONE
            cam?.visibility = View.GONE
        }
    }

    private fun initializeChat() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val receiverId = currentReceiverId
                val userId = currentUserId

                if (userId == null) {
                    Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (receiverId == null) {
                    Toast.makeText(requireContext(), "Invalid receiver", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                currentChatId = findOrCreateChat(userId, receiverId)
                loadChatMessages()

                Log.d("UserChat", "Chat initialized with ID: $currentChatId between $userId and $receiverId")
                }
                catch (e: Exception) {
                Log.e("UserChat", "Error initializing chat", e)
                Toast.makeText(requireContext(), "Error loading chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun findOrCreateChat(senderId: String, receiverId: String): String {
        val participantIds = listOf(senderId, receiverId).sorted()
        val uniqueChatId = "${participantIds[0]}_${participantIds[1]}"

        Log.d("UserChat", "Looking for chat with ID: $uniqueChatId")

        val chatDocRef = db.collection("Chats").document(uniqueChatId)
        val chatDoc = chatDocRef.get().await()

        return if (chatDoc.exists()) {
            Log.d("UserChat", "Using existing chat: $uniqueChatId")
            uniqueChatId
        } else {
            val chatData = hashMapOf(
                "participants" to participantIds,
                "participantsId" to uniqueChatId,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "lastMessage" to "",
                "lastMessageType" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp()
            )

            chatDocRef.set(chatData).await()
            Log.d("UserChat", "Created new chat: $uniqueChatId")
            uniqueChatId
        }
    }

    private fun sendTextMessage() {
        val message = editText.text.toString().trim()
        if (message.isEmpty()) {
            Log.w("UserChat", "Cannot send empty message")
            return
        }

        if (currentChatId == null || currentUserId.isNullOrEmpty() || currentReceiverId.isNullOrEmpty()) {
            Log.w("UserChat", "Cannot send message - missing required data: chatId=$currentChatId, userId=$currentUserId, receiverId=$currentReceiverId")
            Toast.makeText(requireContext(), "Chat not initialized properly", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                sendMessage(currentReceiverId!!, message, MessageType.TEXT, null)

                CoroutineScope(Dispatchers.Main).launch {
                    editText.text?.clear()
                }
            } catch (e: Exception) {
                Log.e("UserChat", "Error sending message", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(requireContext(), "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun sendMessage(
        receiverId: String,
        messageContent: String,
        messageType: MessageType,
        mediaUrl: String?
    ) {
        if (currentChatId == null || currentUserId.isNullOrEmpty()) {
            Log.w("UserChat", "Cannot send message - chat not initialized: chatId=$currentChatId, userId=$currentUserId")
            return
        }

        val messageData = hashMapOf(
            "senderId" to currentUserId!!,
            "receiverId" to receiverId,
            "message" to messageContent,
            "messageType" to messageType.name,
            "mediaUrl" to mediaUrl,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false
        )
        val chatRef = db.collection("Chats").document(currentChatId!!)
        chatRef.update("isReadStatus.${receiverId}", false)


        Log.d("UserChat", "Sending message from $currentUserId to $receiverId in chat $currentChatId")

        db.collection("Chats")
            .document(currentChatId!!)
            .collection("messages")
            .add(messageData)
            .await()

        val chatUpdateData = hashMapOf<String, Any>(
            "lastMessage" to if (messageType == MessageType.TEXT && messageType == MessageType.IMAGE && messageType == MessageType.VOICE) messageContent else messageType.name,
            "lastMessageType" to messageType.name,
            "lastMessageAt" to FieldValue.serverTimestamp()
        )

        db.collection("Chats")
            .document(currentChatId!!)
            .update(chatUpdateData)
            .await()

        Log.d("UserChat", "Message sent successfully to chat: $currentChatId")
    }

    private fun loadChatMessages() {
        if (currentChatId == null) {
            Log.w("UserChat", "Cannot load messages - chat ID is null")
            return
        }

        Log.d("UserChat", "Loading messages for chat: $currentChatId")

        db.collection("Chats")
            .document(currentChatId!!)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UserChat", "Error loading messages", error)
                    return@addSnapshotListener
                }

                if (!isAdded || context == null) {
                    Log.w("UserChat", "Fragment not attached when messages received")
                    return@addSnapshotListener
                }

                Log.d("UserChat", "All Messages: $snapshot")
                userChatList.clear()

                snapshot?.forEach { doc ->
                    try {
                        Log.d("UserChat", "Message doc: $doc")

                        val senderId = doc.getString("senderId") ?: ""
                        val receiverId = doc.getString("receiverId") ?: ""
                        val message = doc.getString("message") ?: ""
                        val timestamp = doc.getTimestamp("timestamp")
                        val messageTypeString = doc.getString("messageType") ?: "TEXT"
                        val mediaUrl = doc.getString("mediaUrl")

                        Log.d("UserChat", "Processing message - SenderId: $senderId, MessageType: $messageTypeString, MediaUrl: $mediaUrl")

                        if ((senderId == currentUserId && receiverId == currentReceiverId) ||
                            (senderId == currentReceiverId && receiverId == currentUserId)) {

                            val time = if (timestamp != null) {
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timestamp.toDate())
                            } else {
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            }

                            val messageType = when (messageTypeString.uppercase()) {
                                "TEXT" -> MessageType.TEXT
                                "IMAGE" -> MessageType.IMAGE
                                "VOICE" -> MessageType.VOICE
                                else -> MessageType.TEXT
                            }

                            val isSentMessage = senderId == currentUserId

                            val messageObj = UserChatDataClass(
                                sendMessage = if (isSentMessage) {
                                    when (messageType) {
                                        MessageType.TEXT -> message
                                        MessageType.IMAGE -> "image"
                                        MessageType.VOICE -> "voice"
                                    }
                                } else null,

                                recieveMessage = if (!isSentMessage) {
                                    when (messageType) {
                                        MessageType.TEXT -> message
                                        MessageType.IMAGE -> "image"
                                        MessageType.VOICE -> "voice"
                                    }
                                } else null,

                                recieverName = arguments?.getString("receiverName") ?: "Unknown",
                                recieverId = currentReceiverId,
                                recieverImage = arguments?.getString("receiverImage"),
                                time = time,
                                messageType = messageType,
                                mediaUrl = mediaUrl,
                                senderId = senderId
                            )

                            userChatList.add(messageObj)
                            Log.d("UserChat", "Added message: Type=${messageType}, IsSent=${isSentMessage}, MediaUrl=${mediaUrl}")
                        }
                    } catch (e: Exception) {
                        Log.e("UserChat", "Error processing message document", e)
                    }
                }

                Log.d("UserChat", "Total messages loaded: ${userChatList.size}")
                adapter.notifyDataSetChanged()
                if (userChatList.isNotEmpty()) {
                    recyclerViews.scrollToPosition(userChatList.size - 1)
                }
            }
    }
    private fun setupCameraLauncher() {
        camLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val receiverId = currentReceiverId ?: return@registerForActivityResult

                val selectedImageUri: Uri? = data?.data
                var imageUrl: String? = null

                if (selectedImageUri != null) {
                    imageUrl = selectedImageUri.toString()
                } else {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    if (bitmap != null) {
                        val path = MediaStore.Images.Media.insertImage(
                            requireContext().contentResolver, bitmap, "IMG_" + System.currentTimeMillis(), null
                        )
                        imageUrl = Uri.parse(path).toString()
                    }
                }

                if (imageUrl != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            sendMessage(receiverId, "", MessageType.IMAGE, imageUrl)
                        } catch (e: Exception) {
                            Log.e("UserChat", "Error sending image", e)
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(requireContext(), "Failed to send image: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openImagePicker() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val chooser = Intent.createChooser(galleryIntent, "Select Image")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        camLauncher.launch(chooser)
    }

    private fun handleVoiceRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }

        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        val audioFile = File(requireContext().externalCacheDir, "audio_${System.currentTimeMillis()}.3gp")
        audioFilePath = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }

        isRecording = true
        Toast.makeText(requireContext(), "Recording started...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        isRecording = false

        val receiverId = currentReceiverId ?: return

        if (audioFilePath != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    sendMessage(receiverId, "", MessageType.VOICE, audioFilePath)
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(requireContext(), "Voice message sent", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("UserChat", "Error sending voice message", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(requireContext(), "Failed to send voice message: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(name: String, image: String, receiverId: String): UserChat {
            val fragment = UserChat()
            val args = Bundle()
            args.putString("receiverName", name)
            args.putString("receiverImage", image)
            args.putString("receiverId", receiverId)
            fragment.arguments = args
            return fragment
        }
    }
}

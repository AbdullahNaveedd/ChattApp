package com.example.chatapp.Fragements

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.callback.ErrorInfo
import com.example.chatapp.Auth.Message.FullScreenActivity
import com.example.chatapp.Call.CallManager
import com.example.chatapp.CallNotification.NotificationTokenManager
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    private var currentRoomId: String? = null
    private var isCallActive = false
    private var isIncomingCall: Boolean = false

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
        currentReceiverId = arguments?.getString("receiverId")


        val receiverImage = arguments?.getString("receiverImage") ?: ""
        Glide.with(requireContext())
            .load(receiverImage)
            .placeholder(R.drawable.profile)
            .circleCrop()
            .into(callimage)

        callimage.setOnClickListener {
            context?.let { ctx ->
                if (receiverImage.isNotEmpty()) {
                    openFullScreenImage(ctx, receiverImage)
                }
            }
        }

//
//        currentReceiverId = arguments?.getString("receiverId")

}
    private fun openFullScreenImage(context: android.content.Context, imageUrl: String) {
        val intent = Intent(context, FullScreenActivity::class.java).apply {
            putExtra(FullScreenActivity.EXTRA_IMAGE_URL, imageUrl)
        }
        context.startActivity(intent)
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
            NotificationTokenManager.getToken()?.let { token ->
                currentUserId?.let { it1 -> NotificationTokenManager.saveTokenToFirestore(it1, token) }
            }

            val callManager = CallManager()
            callManager.initiateCall(
                senderId = currentUserId!!,
                receiverId = currentReceiverId!!,
                onRoomCreated = { roomId, token ->
                    val fragment = VoiceCall().apply {
                        arguments = Bundle().apply {
                            putString("senderId", currentUserId)
                            putString("receiverId", currentReceiverId)
                            putString("roomId", roomId)
                            putBoolean("isCallInitiator", true)
                            putBoolean("isIncomingCall", false) // initiator is not "incoming"
                        }
                    }
                    navigateToFragment(fragment)
                },
                onError = { error ->
                    Log.e("Video", "Error initiating call: $error")
                    Toast.makeText(requireContext(), "Call failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }

        voicebtn.setOnClickListener {
            NotificationTokenManager.getToken()?.let { token ->
                currentUserId?.let { userId ->
                    NotificationTokenManager.saveTokenToFirestore(userId, token)
                }
            }
            val callManager = CallManager()
            callManager.initiateCall(
                senderId = currentUserId!!,
                receiverId = currentReceiverId!!,
                onRoomCreated = { roomId, token ->
                    val fragment = VoiceCall().apply {
                        arguments = Bundle().apply {
                            putString("senderId", currentUserId)
                            putString("receiverId", currentReceiverId)
                            putString("roomId", roomId)
                            putBoolean("isCallInitiator", true)
                            putBoolean("isIncomingCall", false) // initiator is not "incoming"
                        }
                    }
                    navigateToFragment(fragment)
                },
                onError = { error ->
                    Log.e("VoiceCall", "Error initiating call: $error")
                    Toast.makeText(requireContext(), "Call failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
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
        val fragmentTransaction =
            (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
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
                    Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }

                if (receiverId == null) {
                    Toast.makeText(requireContext(), "Invalid receiver", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                currentChatId = findOrCreateChat(userId, receiverId)
                loadChatMessages()

                Log.d(
                    "UserChat",
                    "Chat initialized with ID: $currentChatId between $userId and $receiverId"
                )
            } catch (e: Exception) {
                Log.e("UserChat", "Error initializing chat", e)
                Toast.makeText(
                    requireContext(),
                    "Error loading chat: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
            Log.w(
                "UserChat",
                "Cannot send message - missing required data: chatId=$currentChatId, userId=$currentUserId, receiverId=$currentReceiverId"
            )
            Toast.makeText(requireContext(), "Chat not initialized properly", Toast.LENGTH_SHORT)
                .show()
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
                    Toast.makeText(
                        requireContext(),
                        "Failed to send message: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun uriToFile(uri: Uri, context: Context): File? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri)!!)
            } ?: return null

            val file = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }

            file.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            Log.e("FileConversion", "Error: ${e.message}", e)
            null
        }
    }


    suspend fun uploadFileToCloudinarySuspended(context: Context, file: File): String? =
        suspendCancellableCoroutine { continuation ->

            val config = mapOf(
                "cloud_name" to "dlfn2oyqx",
                "api_key" to "394479351886316",
                "upload_preset" to "chatapp_preset"
            )

            try {
                MediaManager.get()
            } catch (e: Exception) {
                MediaManager.init(context.applicationContext, config)
            }

            MediaManager.get().upload(file.absolutePath)
                .unsigned("chatapp_preset")
                .option("folder", "ChatApp")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        continuation.resume(url, null)
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        continuation.resume(null, null)
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        }


    private fun handleSelectedImage(uri: Uri, receiverId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val file = uriToFile(uri, requireContext())

            if (file == null || file.length() == 0L) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT)
                        .show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show()
            }

            val uploadedUrl = uploadFileToCloudinarySuspended(requireContext(), file)

            withContext(Dispatchers.Main) {
                if (uploadedUrl != null) {
                    Log.d("Cloudinary", "URL: $uploadedUrl")
                    sendMessage(
                        receiverId = receiverId,
                        messageContent = "📷 Photo",
                        messageType = MessageType.IMAGE,
                        mediaUrl = uploadedUrl
                    )
                } else {
                    Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            file.delete()
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

        // Update read status
        val chatRef = db.collection("Chats").document(currentChatId!!)
        chatRef.update("isReadStatus.${receiverId}", false)

        Log.d("UserChat", "Sending message from $currentUserId to $receiverId in chat $currentChatId")
        Log.d("UserChat", "Message data: $messageData")

        // Add message to subcollection
        db.collection("Chats")
            .document(currentChatId!!)
            .collection("messages")
            .add(messageData)
            .await()

        // Update chat document with last message info
        val lastMessageText = when (messageType) {
            MessageType.TEXT -> messageContent
            MessageType.IMAGE -> "📷 Image"
            MessageType.VOICE -> "🎵 Voice message"
        }

        val chatUpdateData = hashMapOf<String, Any>(
            "lastMessage" to lastMessageText,
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
                                        MessageType.IMAGE -> message // Keep the original message (like "📷 Photo")
                                        MessageType.VOICE -> message
                                    }
                                } else null,

                                recieveMessage = if (!isSentMessage) {
                                    when (messageType) {
                                        MessageType.TEXT -> message
                                        MessageType.IMAGE -> message // Keep the original message (like "📷 Photo")
                                        MessageType.VOICE -> message
                                    }
                                } else null,

                                recieverName = arguments?.getString("receiverName") ?: "Unknown",
                                recieverId = currentReceiverId,
                                recieverImage = arguments?.getString("receiverImage"),
                                time = time,
                                messageType = messageType,
                                mediaUrl = mediaUrl, // This is the key - make sure mediaUrl is passed
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
                var finalUri: Uri? = selectedImageUri

                // If taken from camera, convert bitmap to Uri
                if (finalUri == null) {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    if (bitmap != null) {
                        val path = MediaStore.Images.Media.insertImage(
                            requireContext().contentResolver,
                            bitmap,
                            "IMG_${System.currentTimeMillis()}",
                            null
                        )
                        finalUri = Uri.parse(path)
                    }
                }

                finalUri?.let { uri ->
                    handleSelectedImage(uri, receiverId)
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }
        if (!isRecording) startRecording() else stopRecording()
    }

    private fun startRecording() {
        val audioFile = File(requireContext().externalCacheDir, "voice_${System.currentTimeMillis()}.3gp")
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
    suspend fun uploadVoiceToCloudinary(file: File): String? =
        suspendCancellableCoroutine { cont ->

            val config = mapOf(
                "cloud_name" to "dlfn2oyqx",
                "api_key" to "394479351886316",
                "upload_preset" to "chatapp_preset"
            )

            try {
                MediaManager.get()
            } catch (e: Exception) {
                context?.let { MediaManager.init(it.applicationContext, config) }
            }
            try {
            MediaManager.get().upload(file.absolutePath)
                .unsigned("chatapp_preset")
                .option("resource_type", "video") // ⬅ important for .3gp
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                    override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        Log.d("Cloudinary", "Voice uploaded: $url")
                        cont.resume(url, null)
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Log.e("Cloudinary", "Upload error: ${error?.description}")
                        cont.resume(null, null)
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                        cont.resume(null, null)
                    }

                }).dispatch()
        } catch (e: Exception) {
            Log.e("Cloudinary", "Upload exception", e)
            cont.resume(null, null)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val receiverId = currentReceiverId ?: return
            val audioFile = File(audioFilePath ?: return)

            if (!audioFile.exists()) {
                Toast.makeText(requireContext(), "Audio file not found", Toast.LENGTH_SHORT).show()
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Uploading voice...", Toast.LENGTH_SHORT)
                            .show()
                    }

                    val mediaUrl = uploadVoiceToCloudinary(audioFile)

                    withContext(Dispatchers.Main) {
                        if (mediaUrl != null) {
                            sendMessage(receiverId, "🎵 Voice message", MessageType.VOICE, mediaUrl)
                            Toast.makeText(requireContext(), "Voice sent", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Voice upload failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    audioFile.delete()

                } catch (e: Exception) {
                    Log.e("VoiceUpload", "Exception: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Error uploading voice",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Recorder", "Stop failed: ${e.message}", e)
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

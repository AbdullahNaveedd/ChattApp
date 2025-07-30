package com.example.chatapp.Fragements

import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.Call.CallManager
import com.example.chatapp.LiveKt.LiveKitManager
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.renderer.SurfaceViewRenderer
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.RendererCommon

class Videocall : Fragment() {
    private lateinit var videov: ImageView
    private lateinit var messagev: ImageView
    private lateinit var cancelv: ImageView
    private lateinit var micv: ImageView
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private var statusText: TextView? = null

    private var senderId: String? = null
    private var receiverId: String? = null
    private var currentUserId: String? = null
    private var roomId: String? = null
    private var isCallInitiator: Boolean = false
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var callManager: CallManager
    private var currentRoomId: String? = null
    private var isCallActive = false
    private var isCameraEnabled = true
    private var isMicEnabled = true
    private var eglBase: EglBase? = null
    private var hasParticipantJoined = false
    private var isRenderersInitialized = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        arguments?.let {
            senderId = it.getString("senderId")
            receiverId = it.getString("receiverId")
            roomId = it.getString("roomId")
            isCallInitiator = it.getBoolean("isCallInitiator", false)
            currentRoomId = it.getString("roomId")
            val callType = it.getString("callType", "video")

            Log.d("VideocallDebug", "Arguments received:")
            Log.d("VideocallDebug", "senderId: $senderId")
            Log.d("VideocallDebug", "receiverId: $receiverId")
            Log.d("VideocallDebug", "currentUserId: $currentUserId")
            Log.d("VideocallDebug", "roomId: $currentRoomId")
            Log.d("VideocallDebug", "isCallInitiator: $isCallInitiator")
            Log.d("VideocallDebug", "callType: $callType")

            if (senderId == null || receiverId == null) {
                Log.e("VideocallDebug", "Missing required parameters - senderId or receiverId is null")
                return
            }

            if (currentUserId == null) {
                Log.e("VideocallDebug", "Current user is not authenticated")
                return
            }

            if (currentUserId != senderId && currentUserId != receiverId) {
                Log.e("VideocallDebug", "Current user ID doesn't match sender or receiver")
                return
            }

            if (currentRoomId == null) {
                Log.e("VideocallDebug", "Room ID is null!")
                isCallInitiator = true
                Log.d("VideocallDebug", "Setting as call initiator due to null room ID")
            }
        }

        callManager = CallManager()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_videocall, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }

        initializeViews(view)
        setupClickListeners()

        // Add a small delay to ensure views are properly laid out
        view.post {
            initiateOrJoinVideoCall(view)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(CAMERA, RECORD_AUDIO)
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initializeViews(view: View) {
        videov = view.findViewById(R.id.videobtn)
        messagev = view.findViewById(R.id.messagev)
        cancelv = view.findViewById(R.id.cancel)
        micv = view.findViewById(R.id.micv)
        statusText = view.findViewById(R.id.statusText)
        localRenderer = view.findViewById(R.id.localRenderer)
        remoteRenderer = view.findViewById(R.id.remoteRenderer)


        Log.d("Videocall", "Views initialized")
    }

    private fun initializeRenderers() {
        try {
            if (isRenderersInitialized) {
                Log.d("Videocall", "Renderers already initialized, skipping...")
                return
            }

            if (eglBase == null) {
                eglBase = EglBase.create()
                Log.d("Videocall", "EglBase created")
            }

            localRenderer.visibility = View.VISIBLE
            remoteRenderer.visibility = View.VISIBLE

            // ✅ Use the SAME EglBase context for BOTH renderers
            val sharedContext = eglBase!!.eglBaseContext

            try {
                localRenderer.init(sharedContext, null)  // ✅ Use shared context
                localRenderer.setMirror(true)
                localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                localRenderer.setEnableHardwareScaler(true)
                Log.d("Videocall", "Local renderer initialized")
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Already initialized") == true) {
                    Log.w("Videocall", "Local renderer already initialized, continuing...")
                } else {
                    throw e
                }
            }

            try {
                remoteRenderer.init(sharedContext, null)  // ✅ Use shared context
                remoteRenderer.setMirror(false)
                remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                remoteRenderer.setEnableHardwareScaler(true)
                Log.d("Videocall", "Remote renderer initialized")
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Already initialized") == true) {
                    Log.w("Videocall", "Remote renderer already initialized, continuing...")
                } else {
                    throw e
                }
            }

            isRenderersInitialized = true
            Log.d("Videocall", "Both renderers initialized successfully")

        } catch (e: Exception) {
            Log.e("Videocall", "Error initializing renderers", e)
            throw e
        }
    }
    private fun initiateOrJoinVideoCall(view: View) {
        if (currentUserId == null) {
            showErrorAndReturn("User not authenticated")
            return
        }

        if (senderId == null || receiverId == null) {
            showErrorAndReturn("Invalid user IDs")
            return
        }

        updateCallStatus("Connecting...")
        Log.d("Videocall", "Starting video call - CurrentUser: $currentUserId, Sender: $senderId, Receiver: $receiverId")
        Log.d("Videocall", "Room ID: $currentRoomId, IsCallInitiator: $isCallInitiator")

        if (isCallInitiator || currentRoomId == null) {
            updateCallStatus("Starting video call...")
            Log.d("Videocall", "Creating new video call room")

            callManager.initiateCall(
                senderId = currentUserId!!,
                receiverId = receiverId!!,
                onRoomCreated = { roomId, token ->
                    currentRoomId = roomId
                    Log.d("Videocall", "New room created: $roomId")
                    updateCallStatus("Video calling $receiverId...")
                    setupLiveKit(view, roomId, token, currentUserId!!)
                },
                onError = { error ->
                    Log.e("Videocall", "Call initiation error: $error")
                    showErrorAndReturn("Connection failed: $error")
                }
            )
        } else {
            updateCallStatus("Joining video call...")
            Log.d("Videocall", "Joining existing video room: $currentRoomId")

            callManager.joinExistingRoom(
                roomId = currentRoomId!!,
                userId = currentUserId!!,
                onRoomJoined = { roomId, token ->
                    Log.d("Videocall", "Successfully joined video room: $roomId")
                    setupLiveKit(view, roomId, token, currentUserId!!)
                },
                onError = { error ->
                    Log.e("Videocall", "Failed to join video room: $error")
                    Log.d("Videocall", "Fallback: Creating new room instead")
                    updateCallStatus("Creating new video call...")

                    callManager.initiateCall(
                        senderId = currentUserId!!,
                        receiverId = receiverId!!,
                        onRoomCreated = { newRoomId, token ->
                            currentRoomId = newRoomId
                            Log.d("Videocall", "Fallback room created: $newRoomId")
                            updateCallStatus("Video calling $receiverId...")
                            setupLiveKit(view, newRoomId, token, currentUserId!!)
                        },
                        onError = { fallbackError ->
                            Log.e("Videocall", "Fallback call creation failed: $fallbackError")
                            showErrorAndReturn("Failed to start video call: $fallbackError")
                        }
                    )
                }
            )
        }
    }

    private fun setupLiveKit(view: View, roomId: String, token: String, participantName: String) {
        try {
            // Initialize renderers first
            initializeRenderers()

            Log.d("Videocall", "Setting up LiveKit with:")
            Log.d("Videocall", "RoomId: $roomId")
            Log.d("Videocall", "ParticipantName: $participantName")
            Log.d("Videocall", "Token: ${token.take(20)}...")

            liveKitManager = LiveKitManager(
                context = requireContext(),
                serverUrl = "wss://chatapp-gubdfc71.livekit.cloud",
                token = token,
                roomName = roomId,
                participantName = participantName
            )

            liveKitManager.setRenderers(localRenderer, remoteRenderer)

            liveKitManager.connect(
                onConnected = {
                    activity?.runOnUiThread {
                        isCallActive = true
                        Log.d("Videocall", "Connected to video room: $roomId as participant: $participantName")
                        statusText?.visibility=View.GONE

                        liveKitManager.enableAudio(true)
                        isMicEnabled = true
                        Log.d("Videocall", "Audio enabled")

                        Log.d("Videocall", "Enabling video...")
                        liveKitManager.enableVideo(true)
                        isCameraEnabled = true
                        localRenderer.visibility = View.VISIBLE


                        updateButtonStates()

                        if (isCallInitiator) {
                            if (hasParticipantJoined) {
                                updateCallStatus("Video call with $receiverId")
                            } else {
                                updateCallStatus("Waiting for $receiverId to join...")
                            }
                        } else {
                            updateCallStatus("Connected to video call with $senderId")
                        }

                        Log.d("Videocall", "Video call connected successfully")
                    }
                },
                onError = { error ->
                    activity?.runOnUiThread {
                        Log.e("Videocall", "LiveKit connection error: $error")
                        showErrorAndReturn("Connection failed: $error")
                    }
                },
                onParticipantJoined = { joinedParticipantName ->
                    activity?.runOnUiThread {
                        Log.d("Videocall", "Participant joined: $joinedParticipantName")

                        if (joinedParticipantName != participantName) {
                            hasParticipantJoined = true
                            remoteRenderer.visibility = View.VISIBLE

                            if (isCallInitiator) {
                                updateCallStatus("Video call with $receiverId")
                            } else {
                                updateCallStatus("Video call with $senderId")
                            }

                            Log.d("Videocall", "Other participant joined, video call is now active")
                        }
                    }
                },
                onParticipantLeft = { leftParticipantName ->
                    activity?.runOnUiThread {
                        Log.d("Videocall", "Participant left: $leftParticipantName")

                        if (leftParticipantName != participantName) {
                            hasParticipantJoined = false
                            updateCallStatus("$leftParticipantName left the video call")
                            view.postDelayed({
                                endCall()
                            }, 2000)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("Videocall", "Error setting up LiveKit", e)
            showErrorAndReturn("Failed to setup video call: ${e.message}")
        }
    }

    private fun updateCallStatus(status: String) {
        activity?.runOnUiThread {
            Log.d("Videocall", "Status: $status")
            statusText?.text = status
        }
    }

    private fun setupClickListeners() {
        micv.setOnClickListener {
            if (::liveKitManager.isInitialized && liveKitManager.isConnected()) {
                try {
                    isMicEnabled = liveKitManager.toggleMicrophone()
                    updateButtonStates()
                    Log.d("Videocall", if (isMicEnabled) "Mic Enabled" else "Mic Disabled")
                } catch (e: Exception) {
                    Log.e("Videocall", "Error toggling microphone", e)
                }
            }
        }

        videov.setOnClickListener {
            if (::liveKitManager.isInitialized && liveKitManager.isConnected()) {
                try {
                    isCameraEnabled = !isCameraEnabled
                    liveKitManager.enableVideo(isCameraEnabled)
                    localRenderer.visibility = if(isCameraEnabled) View.VISIBLE else View.GONE
                    updateButtonStates()
                    Log.d("Videocall", if (isCameraEnabled) "Camera Enabled" else "Camera Disabled")
                } catch (e: Exception) {
                    Log.e("Videocall", "Error toggling camera", e)
                }
            }
        }

        cancelv.setOnClickListener {
            endCall()
        }

        messagev.setOnClickListener {
            val fragment = UserChat().apply {
                arguments = Bundle().apply {
                    putString("senderId", senderId)
                    putString("receiverId", receiverId)
                }
            }
            replaceFragment(fragment)
        }
    }

    private fun updateButtonStates() {
        activity?.runOnUiThread {
            videov.setImageResource(if (isCameraEnabled) R.drawable.videov else R.drawable.callredicon)
            micv.setImageResource(if (isMicEnabled) R.drawable.micv else R.drawable.callredicon)
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(CAMERA, RECORD_AUDIO)
        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allPermissionsGranted) {
                view?.let {
                    initializeViews(it)
                    setupClickListeners()
                    // Add delay here too
                    it.post {
                        initiateOrJoinVideoCall(it)
                    }
                }
            } else {
                showErrorAndReturn("Camera and microphone permissions are required for video calls")
            }
        }
    }

    private fun endCall() {
        try {
            isCallActive = false

            if (::liveKitManager.isInitialized) {
                liveKitManager.disconnect()
            }

            currentRoomId?.let { roomId ->
                currentUserId?.let { userId ->
                    callManager.endCall(roomId, userId)
                }
            }

            Log.d("Videocall", "Video call ended")
        } catch (e: Exception) {
            Log.e("Videocall", "Error ending call", e)
        }

        replaceFragment(Home())
    }

    private fun showErrorAndReturn(message: String) {
        Log.e("Videocall", message)
        activity?.runOnUiThread {
            updateCallStatus("Error: $message")
        }
        view?.postDelayed({
            replaceFragment(Home())
        }, 1500)
    }

    private fun replaceFragment(fragment: Fragment) {
        try {
            val fragmentTransaction = (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container_view, fragment)
            fragmentTransaction.commit()
        } catch (e: Exception) {
            Log.e("Videocall", "Error replacing fragment", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::liveKitManager.isInitialized) {
                liveKitManager.disconnect()
            }

            if (isCallActive) {
                currentRoomId?.let { roomId ->
                    currentUserId?.let { userId ->
                        callManager.endCall(roomId, userId)
                    }
                }
            }

            // Clean up renderers safely
            cleanupRenderers()

        } catch (e: Exception) {
            Log.e("Videocall", "Error in onDestroy", e)
        }
    }

    private fun cleanupRenderers() {
        try {
            if (::localRenderer.isInitialized) {
                localRenderer.release()
                Log.d("Videocall", "Local renderer released")
            }
        } catch (e: Exception) {
            Log.e("Videocall", "Error releasing local renderer", e)
        }

        try {
            if (::remoteRenderer.isInitialized) {
                remoteRenderer.release()
                Log.d("Videocall", "Remote renderer released")
            }
        } catch (e: Exception) {
            Log.e("Videocall", "Error releasing remote renderer", e)
        }

        try {
            eglBase?.release()
            eglBase = null
            Log.d("Videocall", "EglBase released")
        } catch (e: Exception) {
            Log.e("Videocall", "Error releasing EglBase", e)
        }

        isRenderersInitialized = false
    }

    override fun onPause() {
        super.onPause()
        Log.d("Videocall", "Fragment paused")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Videocall", "Fragment resumed")
    }
}
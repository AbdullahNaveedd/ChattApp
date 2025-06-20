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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.Call.CallManager
import com.example.chatapp.LiveKt.LiveKitManager
import com.example.chatapp.R
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

    private var senderId: String? = null
    private var receiverId: String? = null
    private var roomId: String? = null
    private var isCallInitiator: Boolean = false
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var callManager: CallManager
    private var currentRoomId: String? = null
    private var isCallActive = false
    private var isCameraEnabled = true
    private var isMicEnabled = true
    private var eglBase: EglBase? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            senderId = it.getString("senderId")
            receiverId = it.getString("receiverId")
            roomId = it.getString("roomId")
            isCallInitiator = it.getBoolean("isCallInitiator", false)
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
        setupVideoRenderers()
        setupClickListeners()
        initiateOrJoinVideoCall(view)
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
        localRenderer = view.findViewById(R.id.localRenderer)
        remoteRenderer = view.findViewById(R.id.remoteRenderer)

        Log.d("Videocall", "Views initialized - Local renderer: ${::localRenderer.isInitialized}, Remote renderer: ${::remoteRenderer.isInitialized}")
    }

    private fun setupVideoRenderers() {
        try {
            eglBase?.release()
            eglBase = null

            eglBase = EglBase.create()
            val eglContext = eglBase!!.eglBaseContext

            if (!::remoteRenderer.isInitialized || !::localRenderer.isInitialized) {
                Log.e("Videocall", "Renderers not properly initialized")
                return
            }

            remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            // Z-order for overlaying
            remoteRenderer.setZOrderMediaOverlay(false)
            localRenderer.setZOrderMediaOverlay(true)

            // Mirror settings
            localRenderer.setMirror(true)  // Mirror local video (front camera)
            remoteRenderer.setMirror(false)

            // Make sure they're visible
            remoteRenderer.visibility = View.VISIBLE
            localRenderer.visibility = View.VISIBLE

            Log.d("Videocall", "Video renderers setup completed successfully")
        } catch (e: Exception) {
            Log.e("Videocall", "Error setting up video renderers", e)
            showErrorAndReturn("Failed to setup video: ${e.message}")
        }
    }

    private fun initiateOrJoinVideoCall(view: View) {
        if (senderId == null || receiverId == null) {
            showErrorAndReturn("Invalid user IDs")
            return
        }

        if (roomId != null) {
            updateCallStatus("Switching to video...")
            callManager.joinExistingRoom(
                roomId = roomId!!,
                userId = senderId!!,
                onTokenReceived = { token ->
                    currentRoomId = roomId
                    setupLiveKit(view, roomId!!, token)
                },
                onError = { error ->
                    Log.e("Videocall", "Error joining existing room: $error")
                    showErrorAndReturn("Connection failed: $error")
                }
            )
        } else {
            updateCallStatus("Connecting...")
            Log.d("Videocall", "Starting video call - Sender: $senderId, Receiver: $receiverId")

            callManager.initiateCall(
                senderId = senderId!!,
                receiverId = receiverId!!,
                onRoomCreated = { newRoomId, token ->
                    Log.d("Videocall", "Room created/joined: $newRoomId")
                    currentRoomId = newRoomId

                    if (isCallInitiator) {
                        updateCallStatus("Video calling ${receiverId}...")
                    } else {
                        updateCallStatus("Joining video call...")
                    }
                    setupLiveKit(view, newRoomId, token)
                },
                onError = { error ->
                    Log.e("Videocall", "Call initiation error: $error")
                    showErrorAndReturn("Connection failed: $error")
                }
            )
        }
    }

    private fun setupLiveKit(view: View, roomId: String, token: String) {
        try {
            liveKitManager = LiveKitManager(
                context = requireContext(),
                serverUrl = "wss://chatapp-gubdfc71.livekit.cloud",
                token = token,
                roomName = roomId,
                participantName = senderId ?: "user"
            )

            liveKitManager.connect(
                onConnected = {
                    activity?.runOnUiThread {
                        isCallActive = true
                        updateCallStatus("Video call connected")
                        liveKitManager.setRenderers(localRenderer, remoteRenderer)

                        liveKitManager.enableAudio(true)
                        liveKitManager.enableVideo(true)

                        isMicEnabled = true
                        isCameraEnabled = true
                        updateButtonStates()

                        Log.d("Videocall", "Video call connected successfully")
                    }
                },
                onError = { error ->
                    activity?.runOnUiThread {
                        showErrorAndReturn("Connection failed: $error")
                    }
                },
                onParticipantJoined = { participantName ->
                    activity?.runOnUiThread {
                        updateCallStatus("In video call with $participantName")
                    }
                },
                onParticipantLeft = { participantName ->
                    activity?.runOnUiThread {
                        updateCallStatus("$participantName left the call")
                        view.postDelayed({
                            endCall()
                        }, 2000)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("Videocall", "Error setting up LiveKit", e)
            showErrorAndReturn("Failed to setup call: ${e.message}")
        }
    }

    private fun updateCallStatus(status: String) {
        activity?.runOnUiThread {
            Log.d("Videocall", "Status: $status")
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
                    localRenderer.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
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
            // Update button icons based on current state
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
                // Restart the setup process
                view?.let {
                    initializeViews(it)
                    setupVideoRenderers()
                    setupClickListeners()
                    initiateOrJoinVideoCall(it)
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
                senderId?.let { userId ->
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
                    senderId?.let { userId ->
                        callManager.endCall(roomId, userId)
                    }
                }
            }

            try {
                if (::localRenderer.isInitialized) {
                    localRenderer.release()
                }
            } catch (e: Exception) {
                Log.e("Videocall", "Error releasing local renderer", e)
            }

            try {
                if (::remoteRenderer.isInitialized) {
                    remoteRenderer.release()
                }
            } catch (e: Exception) {
                Log.e("Videocall", "Error releasing remote renderer", e)
            }

            eglBase?.release()
            eglBase = null

        } catch (e: Exception) {
            Log.e("Videocall", "Error in onDestroy", e)
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d("Videocall", "Fragment resumed")
        if (::liveKitManager.isInitialized && liveKitManager.isConnected() && isCameraEnabled) {
            liveKitManager.enableVideo(true)
        }
    }
}
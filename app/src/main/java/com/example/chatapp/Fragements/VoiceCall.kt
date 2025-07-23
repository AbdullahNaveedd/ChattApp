package com.example.chatapp.Fragements

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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.Call.CallManager
import com.example.chatapp.LiveKt.LiveKitManager
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.renderer.SurfaceViewRenderer
import livekit.org.webrtc.EglBase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class VoiceCall : Fragment() {
    private lateinit var backbtn: ImageView
    private lateinit var videov: ImageView
    private lateinit var messagev: ImageView
    private lateinit var cancelv: ImageView
    private lateinit var micv: ImageView
    private lateinit var volumev: ImageView
    private lateinit var imgname: TextView
    private lateinit var voiceimg: ImageView

    private var senderId: String? = null
    private var receiverId: String? = null
    private var currentUserId: String? = null
    private var isCallInitiator: Boolean = false
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var callManager: CallManager
    private var currentRoomId: String? = null
    private var isCallActive = false
    private var hasParticipantJoined = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get current user ID first
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        arguments?.let {
            senderId = it.getString("senderId")
            receiverId = it.getString("receiverId")
            isCallInitiator = it.getBoolean("isCallInitiator", false)
            currentRoomId = it.getString("roomId")

            Log.d("VoiceCallDebug", "Arguments received:")
            Log.d("VoiceCallDebug", "senderId: $senderId")
            Log.d("VoiceCallDebug", "receiverId: $receiverId")
            Log.d("VoiceCallDebug", "currentUserId: $currentUserId")
            Log.d("VoiceCallDebug", "roomId: $currentRoomId")
            Log.d("VoiceCallDebug", "isCallInitiator: $isCallInitiator")

            if (currentRoomId == null) {
                Log.e("VoiceCallDebug", "Room ID is null!")
            }
        }

        callManager = CallManager()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_voice_call, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestMicPermission()
        initializeViews(view)
        setupClickListeners()

        // Show the appropriate name based on role
        if (isCallInitiator) {
            imgname.text = receiverId ?: "Unknown"
        } else {
            imgname.text = senderId ?: "Unknown"
        }

        initiateOrJoinCall(view)
    }

    private fun initializeViews(view: View) {
        micv = view.findViewById(R.id.micv)
        volumev = view.findViewById(R.id.volumev)
        backbtn = view.findViewById(R.id.backbtnv)
        videov = view.findViewById(R.id.videov)
        messagev = view.findViewById(R.id.messagev)
        cancelv = view.findViewById(R.id.cancel)
        imgname = view.findViewById(R.id.imgname)
        voiceimg = view.findViewById(R.id.voiceimg)
    }

    private fun initiateOrJoinCall(view: View) {
        if (currentUserId == null) {
            showErrorAndReturn("User not authenticated")
            return
        }

        if (senderId == null || receiverId == null) {
            showErrorAndReturn("Invalid user IDs")
            return
        }

        updateCallStatus("Connecting...")
        Log.d("VoiceCall", "Starting call - CurrentUser: $currentUserId, Sender: $senderId, Receiver: $receiverId")

        if (isCallInitiator) {
            // For call initiator, use their own ID as senderId
            callManager.initiateCall(
                senderId = currentUserId!!,
                receiverId = receiverId!!,
                onRoomCreated = { roomId, token ->
                    currentRoomId = roomId
                    updateCallStatus("Calling $receiverId...")
                    setupLiveKit(view, roomId, token, currentUserId!!)
                },
                onError = { error ->
                    Log.e("VoiceCall", "Call initiation error: $error")
                    showErrorAndReturn("Connection failed: $error")
                }
            )
        } else {
            // For receiver, join the existing room
            if (currentRoomId == null) {
                showErrorAndReturn("Invalid room ID for incoming call")
                return
            }
            updateCallStatus("Joining call...")

            // Use CallManager's joinExistingRoom method
            callManager.joinExistingRoom(
                roomId = currentRoomId!!,
                userId = currentUserId!!,
                onRoomJoined = { roomId, token ->
                    Log.d("VoiceCall", "Successfully joined room: $roomId")
                    setupLiveKit(view, roomId, token, currentUserId!!)
                },
                onError = { error ->
                    Log.e("VoiceCall", "Failed to join room: $error")
                    showErrorAndReturn("Failed to join call: $error")
                }
            )
        }
    }

    private fun updateCallStatus(status: String) {
        activity?.runOnUiThread {
            imgname.text = status
        }
    }

    private fun setupLiveKit(view: View, roomId: String, token: String, participantName: String) {
        try {
            val localRenderer = view.findViewById<SurfaceViewRenderer>(R.id.localRenderer)
            val remoteRenderer = view.findViewById<SurfaceViewRenderer>(R.id.remoteRenderer)

            val eglBase = EglBase.create()
            localRenderer.setMirror(true)
            localRenderer.visibility = View.GONE

            remoteRenderer.setMirror(false)
            remoteRenderer.visibility = View.GONE

            Log.d("VoiceCall", "Setting up LiveKit with:")
            Log.d("VoiceCall", "RoomId: $roomId")
            Log.d("VoiceCall", "ParticipantName: $participantName")
            Log.d("VoiceCall", "Token: ${token.take(20)}...")

            liveKitManager = LiveKitManager(
                context = requireContext(),
                serverUrl = "wss://chatapp-gubdfc71.livekit.cloud",
                token = token,
                roomName = roomId,
                participantName = participantName // Use the correct participant name
            )

            liveKitManager.connect(
                onConnected = {
                    activity?.runOnUiThread {
                        isCallActive = true
                        Log.d("VoiceCall", "Connected to room: $roomId as participant: $participantName")

                        liveKitManager.setRenderers(localRenderer, remoteRenderer)
                        liveKitManager.enableAudio(true)
                        liveKitManager.enableVideo(false)

                        // Show appropriate status based on role
                        if (isCallInitiator) {
                            if (hasParticipantJoined) {
                                updateCallStatus("In call with $receiverId")
                            } else {
                                updateCallStatus("Waiting for $receiverId to join...")
                            }
                        } else {
                            updateCallStatus("Connected to call with $senderId")
                        }

                        Log.d("VoiceCall", "Voice call connected successfully")
                    }
                },
                onError = { error ->
                    activity?.runOnUiThread {
                        Log.e("VoiceCall", "LiveKit connection error: $error")
                        showErrorAndReturn("Connection failed: $error")
                    }
                },
                onParticipantJoined = { joinedParticipantName ->
                    activity?.runOnUiThread {
                        Log.d("VoiceCall", "Participant joined: $joinedParticipantName")

                        // Only update status for the other participant (not self)
                        if (joinedParticipantName != participantName) {
                            hasParticipantJoined = true

                            if (isCallInitiator) {
                                // Caller sees "In call with [receiver]"
                                updateCallStatus("In call with $receiverId")
                            } else {
                                // Receiver sees "In call with [caller]"
                                updateCallStatus("In call with $senderId")
                            }

                            Log.d("VoiceCall", "Other participant joined, call is now active")
                        }
                    }
                },
                onParticipantLeft = { leftParticipantName ->
                    activity?.runOnUiThread {
                        Log.d("VoiceCall", "Participant left: $leftParticipantName")

                        // If the other participant left, end the call
                        if (leftParticipantName != participantName) {
                            hasParticipantJoined = false
                            updateCallStatus("$leftParticipantName left the call")
                            // Auto-end call after a delay
                            view.postDelayed({
                                endCall()
                            }, 2000)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("VoiceCall", "Error setting up LiveKit", e)
            showErrorAndReturn("Failed to setup call: ${e.message}")
        }
    }

    private fun requestMicPermission() {
        val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsNeeded.toTypedArray(),
                123
            )
        }
    }

    private fun setupClickListeners() {
        micv.setOnClickListener {
            if (::liveKitManager.isInitialized && liveKitManager.isConnected()) {
                try {
                    val isMuted = liveKitManager.toggleMicrophone()
                    micv.setImageResource(if (isMuted) R.drawable.callredicon else R.drawable.micv)
                    Log.d("VoiceCall", if (isMuted) "Mic Muted" else "Mic Unmuted")
                } catch (e: Exception) {
                    Log.e("VoiceCall", "Error toggling microphone", e)
                }
            }
        }

        volumev.setOnClickListener {
            if (::liveKitManager.isInitialized && liveKitManager.isConnected()) {
                try {
                    val isSpeakerOn = liveKitManager.toggleSpeaker()
                    volumev.setImageResource(if (isSpeakerOn) R.drawable.volumev else R.drawable.callredicon)
                    Log.d("VoiceCall", if (isSpeakerOn) "Speaker On" else "Speaker Off")
                } catch (e: Exception) {
                    Log.e("VoiceCall", "Error toggling speaker", e)
                }
            }
        }

        cancelv.setOnClickListener {
            endCall()
        }

        backbtn.setOnClickListener {
            endCall()
        }

        videov.setOnClickListener {
            if (::liveKitManager.isInitialized) {
                Log.d("VoiceCall", "Switching to video call with room: $currentRoomId")
            }

            val fragment = Videocall().apply {
                arguments = Bundle().apply {
                    putString("senderId", senderId)
                    putString("receiverId", receiverId)
                    putString("roomId", currentRoomId)
                    putBoolean("isCallInitiator", isCallInitiator)
                    putBoolean("isIncomingCall", false)
                }
            }

            if (::liveKitManager.isInitialized) {
                liveKitManager.disconnect()
            }

            replaceFragment(fragment)
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

            Log.d("VoiceCall", "Call ended")
        } catch (e: Exception) {
            Log.e("VoiceCall", "Error ending call", e)
        }

        replaceFragment(Home())
    }

    private fun showErrorAndReturn(message: String) {
        Log.e("VoiceCall", message)
        activity?.runOnUiThread {
            imgname.text = "Call Failed"
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
            Log.e("VoiceCall", "Error replacing fragment", e)
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
        } catch (e: Exception) {
            Log.e("VoiceCall", "Error in onDestroy", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("VoiceCall", "Fragment paused")
    }

    override fun onResume() {
        super.onResume()
        Log.d("VoiceCall", "Fragment resumed")
    }
}
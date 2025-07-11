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
import io.livekit.android.renderer.SurfaceViewRenderer
import livekit.org.webrtc.EglBase

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
    private var isCallInitiator: Boolean = false
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var callManager: CallManager
    private var currentRoomId: String? = null
    private var isCallActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            senderId = it.getString("senderId")
            receiverId = it.getString("receiverId")
            isCallInitiator = it.getBoolean("isCallInitiator", false)
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

        receiverId?.let {
            imgname.text = it
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
        if (senderId == null || receiverId == null) {
            showErrorAndReturn("Invalid user IDs")
            return
        }
        updateCallStatus("Connecting...")
        Log.d("VoiceCall", "Starting call - Sender: $senderId, Receiver: $receiverId")

        callManager.initiateCall(
            senderId = senderId!!,
            receiverId = receiverId!!,
            onRoomCreated = { roomId, token ->
                Log.d("VoiceCall", "Room created/joined: $roomId")
                currentRoomId = roomId

                if (isCallInitiator) {
                    updateCallStatus("Calling ${receiverId}...")
                } else {
                    updateCallStatus("Joining call...")
                }
                setupLiveKit(view, roomId, token)
            },
            onError = { error ->
                Log.e("VoiceCall", "Call initiation error: $error")
                showErrorAndReturn("Connection failed: $error")
            }
        )
    }

    private fun updateCallStatus(status: String) {
        activity?.runOnUiThread {
            imgname.text = status
        }
    }

    private fun setupLiveKit(view: View, roomId: String, token: String) {
        try {
            val localRenderer = view.findViewById<SurfaceViewRenderer>(R.id.localRenderer)
            val remoteRenderer = view.findViewById<SurfaceViewRenderer>(R.id.remoteRenderer)

            val eglBase = EglBase.create()
            localRenderer.setMirror(true)
            localRenderer.visibility = View.GONE

            remoteRenderer.setMirror(false)
            remoteRenderer.visibility = View.GONE

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
                        updateCallStatus("Connected")
                        liveKitManager.setRenderers(localRenderer, remoteRenderer)

                        liveKitManager.enableAudio(true)
                        liveKitManager.enableVideo(false)

                        Log.d("VoiceCall", "Voice call connected successfully")
                    }
                },
                onError = { error ->
                    activity?.runOnUiThread {
                        Log.e("VoiceCall", "Connection error: $error")
                        showErrorAndReturn("Connection failed: $error")
                    }
                },
                onParticipantJoined = { participantName ->
                    activity?.runOnUiThread {
                        Log.d("VoiceCall", "Participant joined: $participantName")
                        // Update UI to show call is active
                        if (participantName != senderId) {
                            updateCallStatus("In call with $participantName")
                        }
                    }
                },
                onParticipantLeft = { participantName ->
                    activity?.runOnUiThread {
                        Log.d("VoiceCall", "Participant left: $participantName")

                        // If the other participant left, end the call
                        if (participantName != senderId) {
                            updateCallStatus("$participantName left the call")
                            // Auto-end call after a delay
                            view?.postDelayed({
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
        if (ContextCompat.checkSelfPermission(requireContext(), RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(RECORD_AUDIO),
                1
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
                    val isMuted = liveKitManager.toggleSpeaker()
                    volumev.setImageResource(if (isMuted) R.drawable.callredicon else R.drawable.volumev)
                    Log.d("VoiceCall", if (isMuted) "Speaker Off" else "Speaker On")
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
                liveKitManager.disconnect()
            }

            val fragment = Videocall().apply {
                arguments = Bundle().apply {
                    putString("senderId", senderId)
                    putString("receiverId", receiverId)
                    putString("roomId", currentRoomId)
                    putBoolean("isCallInitiator", isCallInitiator)
                }
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
                senderId?.let { userId ->
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
        imgname.text = "Call Failed"
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
                    senderId?.let { userId ->
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
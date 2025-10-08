package com.example.chatapp.Fragements

import android.Manifest.permission.RECORD_AUDIO
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
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
import com.example.chatapp.Activity.VoiceCallService
import com.example.chatapp.Call.CallManager
import com.example.chatapp.Call.CallRoom
import com.example.chatapp.LiveKt.LiveKitManager
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
    private var receiverName: String? = null
    private var currentUserId: String? = null
    private var isCallInitiator: Boolean = false
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var callManager: CallManager
    private var currentRoomId: String? = null
    private var isCallActive = false
    private var hasParticipantJoined = false
    private val database = FirebaseDatabase.getInstance().reference
    var isSwitchingToVideo: Boolean = false
    private var isFromService: Boolean = false

    // Service related
    private var voiceCallService: VoiceCallService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceCallService.VoiceCallBinder
            voiceCallService = binder.getService()
            isServiceBound = true
            Log.d("VoiceCall", "Service connected")

            // If service already has an active call, get the LiveKitManager
            voiceCallService?.getLiveKitManager()?.let { manager ->
                liveKitManager = manager
                isCallActive = true
                updateUIForActiveCall()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceCallService = null
            isServiceBound = false
            Log.d("VoiceCall", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get current user ID first
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        arguments?.let {
            senderId = it.getString("senderId")
            receiverId = it.getString("receiverId")
            receiverName = it.getString("receiverName")
            isCallInitiator = it.getBoolean("isCallInitiator", false)
            currentRoomId = it.getString("roomId")
            isFromService = it.getBoolean("from_service", false)

            Log.d("VoiceCallDebug", "Arguments received:")
            Log.d("VoiceCallDebug", "senderId: $senderId")
            Log.d("VoiceCallDebug", "receiverId: $receiverId")
            Log.d("VoiceCallDebug", "currentUserId: $currentUserId")
            Log.d("VoiceCallDebug", "receiverName: $receiverName")
            Log.d("VoiceCallDebug", "roomId: $currentRoomId")
            Log.d("VoiceCallDebug", "isCallInitiator: $isCallInitiator")
            Log.d("VoiceCallDebug", "isFromService: $isFromService")

            if (currentRoomId == null) {
                Log.e("VoiceCallDebug", "Room ID is null!")
            }
        }

        callManager = CallManager()

        // Check if service is already running
        if (VoiceCallService.isServiceRunning || isFromService) {
            bindToService()
        }
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
            imgname.text = receiverName ?: "Unknown"
        } else {
            imgname.text = senderId ?: "Unknown"
        }

        // If returning from service or service is running, just update UI
        if (isFromService && VoiceCallService.isServiceRunning) {
            Log.d("VoiceCall", "Returning from service, updating UI")
            updateUIForActiveCall()
        } else if (VoiceCallService.isServiceRunning && voiceCallService?.isCallActive() == true) {
            Log.d("VoiceCall", "Service already running, updating UI")
            updateUIForActiveCall()
        } else {
            Log.d("VoiceCall", "Starting new call")
            initiateOrJoinCall(view)
        }
    }

    private fun bindToService() {
        val intent = Intent(requireContext(), VoiceCallService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
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

    private fun updateUIForActiveCall() {
        activity?.runOnUiThread {
            isCallActive = true
            val displayName = if (isCallInitiator) receiverName else senderId
            imgname.text = "In call with $displayName"
            Log.d("VoiceCall", "UI updated for active call")
        }
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
                recieverName = receiverName,
                onRoomCreated = { roomId, token ->
                    currentRoomId = roomId
                    arguments?.getString("receiverName")?.let {
                        updateCallStatus("Calling $it...")
                    }
                    observeCallStatus(roomId)
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

    private fun observeCallStatus(roomId: String) {
        val callRef = FirebaseDatabase.getInstance().getReference("calls").child(roomId)
        callRef.child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                if (status == "declined") {
                    Log.d("VoiceCall", "Call was declined. Ending call.")
                    endCall()
                } else if (status == "ended") {
                    Log.d("VoiceCall", "Call was ended by the user.")
                    endCall()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("VoiceCall", "Failed to listen to call status: ${error.message}")
            }
        })
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
                participantName = participantName
            )

            liveKitManager.connect(
                onConnected = {
                    activity?.runOnUiThread {
                        isCallActive = true
                        Log.d("VoiceCall", "Connected to room: $roomId as participant: $participantName")

                        liveKitManager.setRenderers(localRenderer, remoteRenderer)
                        liveKitManager.enableAudio(true)
                        liveKitManager.enableVideo(false)

                        // Start the foreground service
                        startVoiceCallService()

                        // Show appropriate status based on role
                        if (isCallInitiator) {
                            if (hasParticipantJoined) {
                                updateCallStatus("In call with $receiverName")
                            } else {
                                updateCallStatus("Waiting for $receiverName to join...")
                            }
                        } else {
                            updateCallStatus("Connected to call with $receiverName")
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

                        if (joinedParticipantName != participantName) {
                            hasParticipantJoined = true

                            if (isCallInitiator) {
                                updateCallStatus("In call with $receiverName")
                            } else {
                                updateCallStatus("In call with $senderId")
                            }

                            Log.d("VoiceCall", "Other participant joined, call is now active")
                        }
                    }
                },
                onParticipantLeft = { leftParticipantName ->
                    activity?.runOnUiThread {
                        Log.d("VoiceCall", "Participant left: $leftParticipantName")

                        if (leftParticipantName != participantName) {
                            hasParticipantJoined = false
                            updateCallStatus("$leftParticipantName left the call")
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

    private fun startVoiceCallService() {
        val serviceIntent = Intent(requireContext(), VoiceCallService::class.java).apply {
            putExtra("roomId", currentRoomId)
            putExtra("participantName", currentUserId)
            putExtra("receiverName", receiverName)
            putExtra("senderId", senderId)
            putExtra("receiverId", receiverId)
            putExtra("isCallInitiator", isCallInitiator)
        }

        requireContext().startForegroundService(serviceIntent)
        bindToService()

        // Set LiveKitManager in service after binding
        view?.postDelayed({
            voiceCallService?.setLiveKitManager(liveKitManager)
        }, 500)
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
            // Use service's LiveKitManager if available, otherwise use local one
            val manager = voiceCallService?.getLiveKitManager() ?: liveKitManager

            if (::liveKitManager.isInitialized && manager.isConnected()) {
                try {
                    val isMuted = manager.toggleMicrophone()
                    micv.setImageResource(if (isMuted) R.drawable.callredicon else R.drawable.micv)
                    Log.d("VoiceCall", if (isMuted) "Mic Muted" else "Mic Unmuted")
                } catch (e: Exception) {
                    Log.e("VoiceCall", "Error toggling microphone", e)
                }
            }
        }

        volumev.setOnClickListener {
            val manager = voiceCallService?.getLiveKitManager() ?: liveKitManager

            if (::liveKitManager.isInitialized && manager.isConnected()) {
                try {
                    val isSpeakerOn = manager.toggleSpeaker()
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
            isSwitchingToVideo = true
            if (::liveKitManager.isInitialized) {
                Log.d("VoiceCall", "Switching to video call with room: $currentRoomId")
            }

            database.child("calls").child(currentRoomId.toString()).child("isNewCall").setValue(false)
                .addOnSuccessListener {
                    Log.d("CallSwitch", "isNewCall set to false successfully")

                    val fragment = Videocall().apply {
                        arguments = Bundle().apply {
                            putString("senderId", senderId)
                            putString("receiverId", receiverId)
                            putString("roomId", currentRoomId)
                            putBoolean("isCallInitiator", isCallInitiator)
                            putBoolean("isSwitchingFromVoice", true)
                        }
                    }

                    // End the voice call service when switching to video
                    voiceCallService?.endCall()

                    replaceFragment(fragment)
                }
                .addOnFailureListener {
                    Log.e("CallSwitch", "Failed to update isNewCall flag", it)
                }
        }

        messagev.setOnClickListener {
            // Don't end the call, just navigate to chat
            // The service will keep the call active
            val fragment = UserChat().apply {
                arguments = Bundle().apply {
                    putString("senderId", senderId)
                    putString("receiverId", receiverId)
                    putString("receiverName", receiverName)
                }
            }
            replaceFragment(fragment)
        }
    }

    private fun endCall() {
        try {
            isCallActive = false

            // End call through service if bound, otherwise handle locally
            if (isServiceBound && voiceCallService != null) {
                voiceCallService?.endCall()
            } else {
                if (::liveKitManager.isInitialized) {
                    liveKitManager.disconnect()
                }

                currentRoomId?.let { roomId ->
                    currentUserId?.let { userId ->
                        callManager.endCall(roomId, userId)
                    }
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
            val fragmentManager = (requireActivity() as AppCompatActivity).supportFragmentManager

            if (!fragmentManager.isStateSaved) {
                fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, fragment)
                    .commit()
            } else {
                fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, fragment)
                    .commitAllowingStateLoss()
            }

        } catch (e: Exception) {
            Log.e("VoiceCall", "Error replacing fragment", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Unbind from service but don't end the call
            if (isServiceBound) {
                requireContext().unbindService(serviceConnection)
                isServiceBound = false
            }

            // Only disconnect if we're not switching to video and service isn't handling the call
            if (!isSwitchingToVideo && !VoiceCallService.isServiceRunning) {
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
            }
        } catch (e: Exception) {
            Log.e("VoiceCall", "Error in onDestroy", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("VoiceCall", "Fragment paused - Service will handle call")
    }

    override fun onResume() {
        super.onResume()
        Log.d("VoiceCall", "Fragment resumed")

        if (VoiceCallService.isServiceRunning && !isServiceBound) {
            bindToService()
        }
    }
}
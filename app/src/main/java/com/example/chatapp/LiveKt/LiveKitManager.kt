package com.example.chatapp.LiveKt

import android.content.Context
import android.util.Log
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import livekit.org.webrtc.EglBase

class LiveKitManager(
    private val context: Context,
    private val serverUrl: String,
    private val token: String,
    private val roomName: String,
    private val participantName: String
) {
    private val scope = MainScope()
    private lateinit var room: Room
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var audioTrack: LocalAudioTrack? = null
    private var videoTrack: LocalVideoTrack? = null

    private var isAudioEnabled = true
    private var isVideoEnabled = false
    private var isSpeakerEnabled = true
    private var isRoomConnected = false

    fun connect(
        onConnected: () -> Unit,
        onError: (String) -> Unit,
        onParticipantJoined: (String) -> Unit = {},
        onParticipantLeft: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                room = LiveKit.create(appContext = context)
                room.audioHandler.start()

                room.connect(
                    url = serverUrl,
                    token = token,
                    options = ConnectOptions(autoSubscribe = true)
                )

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true

                initializeRenderersWithRoom()

                createAndPublishAudioTrack()

                observeRoomEvents(onParticipantJoined, onParticipantLeft)

                isRoomConnected = true
                onConnected()
                Log.d("LiveKit", "Connected to room: ${room.name} and ${room.localParticipant}")
                Log.d("LiveKit", "Connecting to room with token: $token and server: $serverUrl")
                Log.d("LiveKit", "AudioManager Mode: ${audioManager.mode}, Speakerphone ON: ${audioManager.isSpeakerphoneOn}")

            } catch (e: Exception) {
                Log.e("LiveKit", "Connection failed", e)
                isRoomConnected = false
                onError(e.localizedMessage ?: "Connection failed")
            }
        }
    }

    private fun initializeRenderersWithRoom() {
        try {
            // Only initialize renderers if they haven't been initialized with the room yet
            localRenderer?.let { renderer ->
                try {
                    room.initVideoRenderer(renderer)
                    Log.d("LiveKit", "Local renderer initialized with room")
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("Already initialized") == true) {
                        Log.w("LiveKit", "Local renderer already initialized with room")
                    } else {
                        Log.e("LiveKit", "Error initializing local renderer with room", e)
                        throw e
                    }
                }
            }

            remoteRenderer?.let { renderer ->
                try {
                    room.initVideoRenderer(renderer)
                    Log.d("LiveKit", "Remote renderer initialized with room")
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("Already initialized") == true) {
                        Log.w("LiveKit", "Remote renderer already initialized with room")
                    } else {
                        Log.e("LiveKit", "Error initializing remote renderer with room", e)
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LiveKit", "Error initializing renderers with room", e)
            throw e
        }
    }

    private fun createAndPublishAudioTrack() {
        scope.launch {
            try {
                val audioTrack = room.localParticipant.createAudioTrack()
                room.localParticipant.publishAudioTrack(audioTrack)
                room.localParticipant.setMicrophoneEnabled(true)
                this@LiveKitManager.audioTrack = audioTrack

                Log.d("LiveKit", "Audio track created and published successfully")
            } catch (e: Exception) {
                Log.e("LiveKit", "Failed to create/publish audio track", e)
            }
        }
    }

    fun createAndPublishVideoTrack() {
        scope.launch {
            try {
                if (::room.isInitialized && isRoomConnected) {
                    videoTrack = room.localParticipant.createVideoTrack()
                    room.localParticipant.publishVideoTrack(videoTrack!!)
                    Log.d("LiveKit", "Video track created and published successfully")

                    localRenderer?.let { renderer ->
                        try {
                            videoTrack?.addRenderer(renderer)
                            Log.d("LiveKit", "Local video renderer attached to track")
                        } catch (e: Exception) {
                            Log.e("LiveKit", "Error attaching local renderer to track", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Failed to create/publish video track", e)
            }
        }
    }

    private fun observeRoomEvents(
        onParticipantJoined: (String) -> Unit,
        onParticipantLeft: (String) -> Unit
    ) {
        scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        val name = event.participant.identity
                        Log.d("LiveKit", "Participant joined: $name")
                        onParticipantJoined(name.toString())
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        val name = event.participant.identity
                        Log.d("LiveKit", "Participant left: $name")
                        onParticipantLeft(name.toString())
                    }

                    is RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        val participant = event.participant
                        Log.d("LiveKit", "Track subscribed from: ${participant.identity}, Track type: ${track?.javaClass?.simpleName}")

                        when (track) {
                            is RemoteVideoTrack -> {
                                remoteRenderer?.let { renderer ->
                                    try {
                                        track.addRenderer(renderer)
                                        Log.d("LiveKit", "Remote video track added to renderer")
                                    } catch (e: Exception) {
                                        Log.e("LiveKit", "Error adding remote video track to renderer", e)
                                    }
                                }
                            }

                            is RemoteAudioTrack -> {
                                Log.d("LiveKit", "Remote audio track received - audio should now work")
                            }
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
                        Log.d("LiveKit", "Track unsubscribed: ${event.track?.name}")
                    }

                    is RoomEvent.Disconnected -> {
                        Log.d("LiveKit", "Disconnected from room")
                        isRoomConnected = false
                    }

                    is RoomEvent.Connected -> {
                        Log.d("LiveKit", "Connected to room")
                        isRoomConnected = true
                    }

                    else -> {
                        Log.d("LiveKit", "Unhandled event: ${event::class.simpleName}")
                    }
                }
            }
        }
    }

    fun setRenderers(localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        Log.d("LiveKit", "Setting renderers")

        this.localRenderer = localRenderer
        this.remoteRenderer = remoteRenderer

        // If room is already connected, initialize renderers with room
        if (::room.isInitialized && isRoomConnected) {
            try {
                initializeRenderersWithRoom()

                // Attach existing video track to local renderer if it exists
                videoTrack?.let { track ->
                    try {
                        track.removeRenderer(localRenderer)

                        track.addRenderer(localRenderer)
                        Log.d("LiveKit", "Existing video track attached to local renderer")
                    } catch (e: Exception) {
                        Log.e("LiveKit", "Error attaching existing video track", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Error setting up renderers", e)
            }
        } else {
            Log.d("LiveKit", "Room not yet connected, renderers will be initialized on connection")
        }
    }
    fun attachVideoToLocalRenderer() {
        scope.launch {
            try {
                if (::room.isInitialized && isRoomConnected && videoTrack != null) {
                    localRenderer?.let { renderer ->
                        Log.d("LiveKit", "Force attaching existing video track to local renderer...")
                        videoTrack?.removeRenderer(renderer) // Remove first
                        videoTrack?.addRenderer(renderer)    // Then add
                        renderer.requestLayout()
                        renderer.invalidate()

                        Log.d("LiveKit", "✅ Video track force-attached to local renderer")
                    }
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Error force-attaching video track", e)
            }
        }
    }
    fun enableAudio(enable: Boolean) {
        scope.launch {
            try {
                if (::room.isInitialized && isRoomConnected) {
                    room.localParticipant.setMicrophoneEnabled(enable)
                    isAudioEnabled = enable
                    Log.d("LiveKit", "Audio ${if (enable) "enabled" else "disabled"}")
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Failed to toggle audio", e)
            }
        }
    }


    fun enableVideo(enable: Boolean) {
        scope.launch {
            try {
                Log.d("LiveKit", "enableVideo called with enable=$enable")
                Log.d("LiveKit", "Room initialized: ${::room.isInitialized}, Connected: $isRoomConnected")
                Log.d("LiveKit", "Local renderer available: ${localRenderer != null}")

                if (::room.isInitialized && isRoomConnected) {
                    if (enable) {
                        room.localParticipant.setCameraEnabled(true)
                        Log.d("LiveKit", "Camera enabled first")

                        if (videoTrack == null) {
                            Log.d("LiveKit", "Creating new video track...")

                            // Create video track
                            videoTrack = room.localParticipant.createVideoTrack()
                            Log.d("LiveKit", "Video track created: ${videoTrack != null}")

                            // Publish video track
                            room.localParticipant.publishVideoTrack(videoTrack!!)
                            Log.d("LiveKit", "Video track published successfully")

                            // Attach to local renderer
                            localRenderer?.let { renderer ->
                                try {
                                    videoTrack?.removeRenderer(renderer)

                                    videoTrack?.addRenderer(renderer)
                                    renderer.requestLayout()
                                    renderer.invalidate()
                                } catch (e: Exception) {
                                }
                            } ?: Log.e("LiveKit", "❌ Local renderer is null!")

                        } else {
                            Log.d("LiveKit", "Video track already exists, just enabling camera")
                        }

                    } else {
                        room.localParticipant.setCameraEnabled(false)
                        Log.d("LiveKit", "Camera disabled")
                    }

                    isVideoEnabled = enable
                    Log.d("LiveKit", "✅ Video ${if (enable) "enabled" else "disabled"} successfully")

                } else {
                    Log.e("LiveKit", "❌ Cannot enable video - Room not ready. Initialized: ${::room.isInitialized}, Connected: $isRoomConnected")
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "❌ Failed to toggle video", e)
            }
        }
    }
    fun toggleVideo(): Boolean {
        val newState = !isVideoEnabled
        enableVideo(newState)
        return newState
    }

    fun toggleMicrophone(): Boolean {
        isAudioEnabled = !isAudioEnabled
        enableAudio(isAudioEnabled)
        return !isAudioEnabled
    }

    fun toggleSpeaker(): Boolean {
        isSpeakerEnabled = !isSpeakerEnabled
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = isSpeakerEnabled
        Log.d("LiveKit", "Speaker ${if (isSpeakerEnabled) "enabled" else "disabled"}")
        return isSpeakerEnabled
    }

    fun disconnect() {
        scope.launch {
            try {
                if (::room.isInitialized) {
                    room.audioHandler.stop()
                    audioTrack?.let {
                        room.localParticipant.unpublishTrack(it)
                        it.stop()
                    }
                    videoTrack?.let {
                        room.localParticipant.unpublishTrack(it)
                        it.stop()
                    }

                    room.disconnect()
                    Log.d("LiveKit", "Disconnected from room")
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Error during disconnect", e)
            }
        }
    }

    fun isConnected(): Boolean {
        return ::room.isInitialized && room.state == Room.State.CONNECTED
    }
}

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

                localRenderer?.let { room.initVideoRenderer(it) }
                remoteRenderer?.let { room.initVideoRenderer(it) }

                createAndPublishAudioTrack()
                createAndPublishVideoTrack()

                observeRoomEvents(onParticipantJoined, onParticipantLeft)

                onConnected()
                Log.d("LiveKit", "Connected to room: ${room.name} and ${room.localParticipant}")
                Log.d("LiveKit", "Connecting to room with token: $token and server: $serverUrl")
                Log.d("LiveKit", "AudioManager Mode: ${audioManager.mode}, Speakerphone ON: ${audioManager.isSpeakerphoneOn}")

            } catch (e: Exception) {
                Log.e("LiveKit", "Connection failed", e)
                onError(e.localizedMessage ?: "Connection failed")
            }
        }
    }

    private fun createAndPublishAudioTrack() {
        scope.launch {
            try {
                val audioTrack = room.localParticipant.createAudioTrack()
                room.localParticipant.publishAudioTrack(audioTrack)
                room.localParticipant.setMicrophoneEnabled(true)

                Log.d("LiveKit", "Audio track created and published successfully")
            } catch (e: Exception) {
                Log.e("LiveKit", "Failed to create/publish audio track", e)
            }
        }
    }


    fun createAndPublishVideoTrack() {
        scope.launch {
            try {
                if (::room.isInitialized) {
                    videoTrack = room.localParticipant.createVideoTrack()
                    room.localParticipant.publishVideoTrack(videoTrack!!)
                    Log.d("LiveKit", "Video track created and published successfully")

                    localRenderer?.let {
                        videoTrack?.addRenderer(it)
                        Log.d("LiveKit", "Local video renderer attached")
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
                                    track.addRenderer(renderer)
                                    Log.d("LiveKit", "Remote video track added to renderer")
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
                    }

                    else -> {
                        Log.d("LiveKit", "Unhandled event: ${event::class.simpleName}")
                    }
                }
            }
        }
    }

    fun setRenderers(localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        this.localRenderer = localRenderer
        this.remoteRenderer = remoteRenderer

        videoTrack?.let { track ->
            track.addRenderer(localRenderer)
            Log.d("LiveKit", "Video renderer re-attached in setRenderers")
        } ?: run {
            createAndPublishVideoTrack()
        }
    }

    fun enableAudio(enable: Boolean) {
        scope.launch {
            try {
                if (::room.isInitialized) {
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
                if (::room.isInitialized) {
                    room.localParticipant.setCameraEnabled(enable)
                    isVideoEnabled = enable
                    Log.d("LiveKit", "Video ${if (enable) "enabled" else "disabled"}")
                }
            } catch (e: Exception) {
                Log.e("LiveKit", "Failed to toggle video", e)
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

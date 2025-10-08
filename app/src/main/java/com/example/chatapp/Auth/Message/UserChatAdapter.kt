package com.example.chatapp.Auth.Message

import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth

class UserChatAdapter(
    private val usermessage: List<UserChatDataClass>,
    private val onCallClick: (isVoiceCall: Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mediaPlayers = mutableMapOf<Int, MediaPlayer>()
    private val handlers = mutableMapOf<Int, Handler>()
    private val updateRunnables = mutableMapOf<Int, Runnable>()

    class UserChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderMessage: TextView = itemView.findViewById(R.id.sendermessage)
        val senderTime: TextView = itemView.findViewById(R.id.sendertime)
        val messageType: ImageView = itemView.findViewById(R.id.senderimage)
        val videoView: VideoView = itemView.findViewById(R.id.sendervideo)
        val voiceMessageLayout: TextView = itemView.findViewById(R.id.voicemessage)
        val btnPlayPause: ImageView = itemView.findViewById(R.id.btnPlayPause)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        val receiverName: TextView = itemView.findViewById(R.id.revievermessagename)
        val receiverMessage: TextView = itemView.findViewById(R.id.recievermessage)
        val receiverImage: ImageView = itemView.findViewById(R.id.recieverimage)
        val receiverTime: TextView = itemView.findViewById(R.id.time)

        val receiverImageMessage: ImageView? = itemView.findViewById(R.id.rsenderimage)
        val receiverVideoView: VideoView? = itemView.findViewById(R.id.rsendervideo)
        val receiverVoiceLayout: TextView? = itemView.findViewById(R.id.rvoicemessage)
        val receiverBtnPlayPause: ImageView? = itemView.findViewById(R.id.rbtnPlayPause)
        val receiverProgressBar: ProgressBar? = itemView.findViewById(R.id.reciverprogressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.userchatmessage, parent, false)
        return UserChatViewHolder(view)
    }

    override fun getItemCount(): Int {
        return usermessage.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = usermessage[position]
        val viewHolder = holder as UserChatViewHolder

        Log.d("UserChatAdapter", "Message at position $position: Type=${data.messageType}, MediaUrl=${data.mediaUrl}")

        cleanupMediaPlayer(position)
        resetVisibility(viewHolder)

        val isSentMessage = data.senderId == getCurrentUserId()

        if (isSentMessage) {
            showSenderMessage(viewHolder, data, position)
            hideReceiverMessage(viewHolder)
        } else {
            showReceiverMessage(viewHolder, data, position)
            hideSenderMessage(viewHolder)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is UserChatViewHolder) {
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                cleanupMediaPlayer(position)
                holder.videoView.stopPlayback()
                holder.receiverVideoView?.stopPlayback()
            }
        }
    }

    private fun resetVisibility(viewHolder: UserChatViewHolder) {
        viewHolder.senderMessage.visibility = View.GONE
        viewHolder.messageType.visibility = View.GONE
        viewHolder.videoView.visibility = View.GONE
        viewHolder.voiceMessageLayout.visibility = View.GONE
        viewHolder.receiverMessage.visibility = View.GONE
        viewHolder.receiverName.visibility = View.GONE
        viewHolder.receiverImage.visibility = View.GONE
        viewHolder.receiverTime.visibility = View.GONE
        viewHolder.senderTime.visibility = View.GONE
        viewHolder.receiverImageMessage?.visibility = View.GONE
        viewHolder.receiverVideoView?.visibility = View.GONE
        viewHolder.receiverVoiceLayout?.visibility = View.GONE
    }

    private fun loadImageWithGlide(
        context: android.content.Context,
        imageView: ImageView,
        mediaUrl: String,
        placeholderRes: Int = R.drawable.profile,
        errorRes: Int = R.drawable.profile
    ) {
        if (mediaUrl.isBlank()) {
            Log.e("UserChatAdapter", "Media URL is empty")
            imageView.setImageResource(errorRes)
            return
        }

        Log.d("UserChatAdapter", "Loading image: $mediaUrl")

        Glide.with(context)
            .load(mediaUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(placeholderRes)
            .error(errorRes)
            .timeout(60000)
            .override(400, 400)
            .centerCrop()
            .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("UserChatAdapter", "Failed to load image: $model")
                    e?.logRootCauses("UserChatAdapter")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d("UserChatAdapter", "Successfully loaded image: $model")
                    return false
                }
            })
            .into(imageView)
        imageView.setOnClickListener {
            openFullScreenImage(context, mediaUrl)
        }
    }

    private fun openFullScreenImage(context: android.content.Context, imageUrl: String) {
        val intent = Intent(context, FullScreenActivity::class.java).apply {
            putExtra(FullScreenActivity.EXTRA_IMAGE_URL, imageUrl)
        }
        context.startActivity(intent)
    }

    private fun setupVideoView(videoView: VideoView, videoUrl: String) {
        try {
            val mediaController = MediaController(videoView.context)
            mediaController.setAnchorView(videoView)

            videoView.setMediaController(mediaController)
            videoView.setVideoURI(Uri.parse(videoUrl))

            videoView.setOnPreparedListener { mp ->
                mp.setOnVideoSizeChangedListener { _, _, _ ->
                    mediaController.setAnchorView(videoView)
                }
            }

            videoView.setOnErrorListener { _, what, extra ->
                Log.e("UserChatAdapter", "VideoView error: what=$what, extra=$extra")
                true
            }

            Log.d("UserChatAdapter", "Video URL set: $videoUrl")
        } catch (e: Exception) {
            Log.e("UserChatAdapter", "Error setting up video", e)
        }
    }

    private fun showSenderMessage(viewHolder: UserChatViewHolder, data: UserChatDataClass, position: Int) {
        viewHolder.senderTime.visibility = View.VISIBLE
        viewHolder.senderTime.text = data.time

        when (data.messageType) {
            MessageType.TEXT -> {
                viewHolder.senderMessage.visibility = View.VISIBLE
                viewHolder.senderMessage.text = data.sendMessage ?: data.recieveMessage
            }

            MessageType.IMAGE -> {
                viewHolder.messageType.visibility = View.VISIBLE

                if (!data.mediaUrl.isNullOrEmpty()) {
                    loadImageWithGlide(
                        context = viewHolder.itemView.context,
                        imageView = viewHolder.messageType,
                        mediaUrl = data.mediaUrl!!,
                        errorRes = R.drawable.profile
                    )
                } else {
                    Log.e("UserChatAdapter", "Media URL is null or empty for sender image")
                    viewHolder.messageType.setImageResource(R.drawable.profile)
                }
            }

            MessageType.VIDEO -> {
                viewHolder.videoView.visibility = View.VISIBLE
                if (!data.mediaUrl.isNullOrEmpty()) {
                    setupVideoView(viewHolder.videoView, data.mediaUrl!!)
                }
            }

            MessageType.VOICE -> {
                viewHolder.voiceMessageLayout.visibility = View.VISIBLE
                if (!data.mediaUrl.isNullOrEmpty()) {
                    setupVoiceMessage(viewHolder, data, position, true)
                }
            }
        }
    }

    private fun showReceiverMessage(viewHolder: UserChatViewHolder, data: UserChatDataClass, position: Int) {
        viewHolder.receiverTime.visibility = View.VISIBLE
        viewHolder.receiverName.visibility = View.VISIBLE
        viewHolder.receiverImage.visibility = View.VISIBLE

        viewHolder.receiverTime.text = data.time
        viewHolder.receiverName.text = data.recieverName

        Glide.with(viewHolder.itemView.context)
            .load(data.recieverImage)
            .circleCrop()
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(viewHolder.receiverImage)

        if(data.messageType == MessageType.VOICE) {
            viewHolder.receiverBtnPlayPause?.visibility = View.VISIBLE
            viewHolder.receiverProgressBar?.visibility = View.VISIBLE
        } else {
            viewHolder.receiverBtnPlayPause?.visibility = View.GONE
            viewHolder.receiverProgressBar?.visibility = View.GONE
        }

        when (data.messageType) {
            MessageType.TEXT -> {
                viewHolder.receiverMessage.visibility = View.VISIBLE
                viewHolder.receiverMessage.text = data.recieveMessage ?: data.sendMessage
            }

            MessageType.IMAGE -> {
                viewHolder.receiverImageMessage?.let { imageView ->
                    imageView.visibility = View.VISIBLE

                    if (!data.mediaUrl.isNullOrEmpty()) {
                        loadImageWithGlide(
                            context = viewHolder.itemView.context,
                            imageView = imageView,
                            mediaUrl = data.mediaUrl!!,
                            placeholderRes = R.drawable.profile,
                            errorRes = R.drawable.profile
                        )
                    } else {
                        Log.e("UserChatAdapter", "Media URL is null or empty for receiver image")
                        imageView.setImageResource(R.drawable.profile)
                    }
                } ?: run {
                    viewHolder.receiverMessage.visibility = View.VISIBLE
                    viewHolder.receiverMessage.text = "📷 Image"
                }
            }

            MessageType.VIDEO -> {
                viewHolder.receiverVideoView?.let { videoView ->
                    videoView.visibility = View.VISIBLE
                    if (!data.mediaUrl.isNullOrEmpty()) {
                        setupVideoView(videoView, data.mediaUrl!!)
                    }
                } ?: run {
                    viewHolder.receiverMessage.visibility = View.VISIBLE
                    viewHolder.receiverMessage.text = "🎥 Video"
                }
            }

            MessageType.VOICE -> {
                viewHolder.receiverVoiceLayout?.let { voiceLayout ->
                    voiceLayout.visibility = View.VISIBLE
                    if (!data.mediaUrl.isNullOrEmpty()) {
                        setupVoiceMessage(viewHolder, data, position, false)
                    }
                } ?: run {
                    viewHolder.receiverMessage.visibility = View.VISIBLE
                    viewHolder.receiverMessage.text = "🎵 Voice Message"
                }
            }
        }
    }

    private fun hideSenderMessage(viewHolder: UserChatViewHolder) {
        viewHolder.senderMessage.visibility = View.GONE
        viewHolder.messageType.visibility = View.GONE
        viewHolder.videoView.visibility = View.GONE
        viewHolder.voiceMessageLayout.visibility = View.GONE
        viewHolder.senderTime.visibility = View.GONE
    }

    private fun hideReceiverMessage(viewHolder: UserChatViewHolder) {
        viewHolder.receiverMessage.visibility = View.GONE
        viewHolder.receiverName.visibility = View.GONE
        viewHolder.receiverImage.visibility = View.GONE
        viewHolder.receiverTime.visibility = View.GONE
        viewHolder.receiverImageMessage?.visibility = View.GONE
        viewHolder.receiverVideoView?.visibility = View.GONE
        viewHolder.receiverVoiceLayout?.visibility = View.GONE
        viewHolder.receiverBtnPlayPause?.visibility = View.GONE
        viewHolder.receiverProgressBar?.visibility = View.GONE
    }

    private fun setupVoiceMessage(viewHolder: UserChatViewHolder, data: UserChatDataClass, position: Int, isSender: Boolean) {
        val progressBar = if (isSender) viewHolder.progressBar else viewHolder.receiverProgressBar
        val btnPlayPause = if (isSender) viewHolder.btnPlayPause else viewHolder.receiverBtnPlayPause

        if (progressBar == null || btnPlayPause == null) {
            Log.e("UserChatAdapter", "ProgressBar or PlayPause button is null")
            return
        }

        progressBar.progress = 0
        btnPlayPause.setImageResource(R.drawable.play)
        btnPlayPause.tag = false

        val handler = Handler(Looper.getMainLooper())
        handlers[position] = handler

        val updateRunnable = object : Runnable {
            override fun run() {
                mediaPlayers[position]?.let { player ->
                    try {
                        if (player.isPlaying) {
                            progressBar.progress = player.currentPosition
                            handler.postDelayed(this, 100)
                        }
                    } catch (e: Exception) {
                        Log.e("UserChatAdapter", "Error updating progress", e)
                    }
                }
            }
        }
        updateRunnables[position] = updateRunnable

        btnPlayPause.setOnClickListener {
            val isPlaying = btnPlayPause.tag as? Boolean ?: false

            if (!isPlaying) {
                btnPlayPause.setImageResource(R.drawable.pause)
                btnPlayPause.tag = true

                if (mediaPlayers[position] == null) {
                    val mediaPlayer = MediaPlayer().apply {
                        try {
                            val audioUrl = data.mediaUrl!!
                            Log.d("UserChatAdapter", "Using audio URL: $audioUrl")

                            setDataSource(audioUrl)
                            prepareAsync()

                            setOnPreparedListener { player ->
                                try {
                                    progressBar.max = player.duration
                                    player.start()
                                    handler.post(updateRunnable)
                                } catch (e: Exception) {
                                    Log.e("UserChatAdapter", "Error starting playback", e)
                                    resetPlayButton(btnPlayPause, progressBar)
                                }
                            }

                            setOnCompletionListener {
                                handler.removeCallbacks(updateRunnable)
                                progressBar.progress = 0
                                btnPlayPause.setImageResource(R.drawable.play)
                                btnPlayPause.tag = false
                                cleanupMediaPlayer(position)
                            }

                            setOnErrorListener { _, what, extra ->
                                Log.e("UserChatAdapter", "MediaPlayer error: what=$what, extra=$extra")
                                handler.removeCallbacks(updateRunnable)
                                resetPlayButton(btnPlayPause, progressBar)
                                cleanupMediaPlayer(position)
                                true
                            }
                        } catch (e: Exception) {
                            Log.e("UserChatAdapter", "Error setting up MediaPlayer", e)
                            resetPlayButton(btnPlayPause, progressBar)
                        }
                    }

                    mediaPlayers[position] = mediaPlayer
                } else {
                    try {
                        mediaPlayers[position]?.start()
                        handler.post(updateRunnable)
                    } catch (e: Exception) {
                        Log.e("UserChatAdapter", "Error resuming playback", e)
                        resetPlayButton(btnPlayPause, progressBar)
                    }
                }
            } else {
                btnPlayPause.setImageResource(R.drawable.play)
                btnPlayPause.tag = false
                handler.removeCallbacks(updateRunnable)
                try {
                    mediaPlayers[position]?.pause()
                } catch (e: Exception) {
                    Log.e("UserChatAdapter", "Error pausing playback", e)
                }
            }
        }
    }

    private fun resetPlayButton(btnPlayPause: ImageView, progressBar: ProgressBar) {
        btnPlayPause.setImageResource(R.drawable.play)
        btnPlayPause.tag = false
        progressBar.progress = 0
    }

    private fun cleanupMediaPlayer(position: Int) {
        mediaPlayers[position]?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e("UserChatAdapter", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayers.remove(position)

        updateRunnables[position]?.let { runnable ->
            handlers[position]?.removeCallbacks(runnable)
        }
        updateRunnables.remove(position)
        handlers.remove(position)
    }

    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    fun cleanup() {
        for (position in mediaPlayers.keys.toList()) {
            cleanupMediaPlayer(position)
        }
    }
}
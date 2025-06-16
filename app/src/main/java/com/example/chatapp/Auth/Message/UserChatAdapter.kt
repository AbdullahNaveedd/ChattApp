package com.example.chatapp.Auth.Message

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import com.bumptech.glide.Glide
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
        val voiceMessageLayout: TextView = itemView.findViewById(R.id.voicemessage)
        val btnPlayPause: ImageView = itemView.findViewById(R.id.btnPlayPause)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        val receiverName: TextView = itemView.findViewById(R.id.revievermessagename)
        val receiverMessage: TextView = itemView.findViewById(R.id.recievermessage)
        val receiverImage: ImageView = itemView.findViewById(R.id.recieverimage)
        val receiverTime: TextView = itemView.findViewById(R.id.time)

        val receiverImageMessage: ImageView? = itemView.findViewById(R.id.rsenderimage)
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
            }
        }
    }

    private fun resetVisibility(viewHolder: UserChatViewHolder) {
        viewHolder.senderMessage.visibility = View.GONE
        viewHolder.messageType.visibility = View.GONE
        viewHolder.voiceMessageLayout.visibility = View.GONE
        viewHolder.receiverMessage.visibility = View.GONE
        viewHolder.receiverName.visibility = View.GONE
        viewHolder.receiverImage.visibility = View.GONE
        viewHolder.receiverTime.visibility = View.GONE
        viewHolder.senderTime.visibility = View.GONE
        viewHolder.receiverImageMessage?.visibility = View.GONE
        viewHolder.receiverVoiceLayout?.visibility = View.GONE
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
                    Glide.with(viewHolder.itemView.context)
                        .load(data.mediaUrl)
                        .placeholder(R.drawable.user)
                        .error(R.drawable.profile)
                        .into(viewHolder.messageType)
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
            .into(viewHolder.receiverImage)

        if(data.messageType==MessageType.VOICE)
        {
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
                        Glide.with(viewHolder.itemView.context)
                            .load(data.mediaUrl)
                            .placeholder(R.drawable.user)
                            .error(R.drawable.profile)
                            .into(imageView)
                    }
                } ?: run {
                    viewHolder.receiverMessage.visibility = View.VISIBLE
                    viewHolder.receiverMessage.text = "📷 Image"
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
        viewHolder.voiceMessageLayout.visibility = View.GONE
        viewHolder.senderTime.visibility = View.GONE

    }

    private fun hideReceiverMessage(viewHolder: UserChatViewHolder) {
        viewHolder.receiverMessage.visibility = View.GONE
        viewHolder.receiverName.visibility = View.GONE
        viewHolder.receiverImage.visibility = View.GONE
        viewHolder.receiverTime.visibility = View.GONE
        viewHolder.receiverImageMessage?.visibility = View.GONE
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
                        } else {

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
                            setDataSource(data.mediaUrl)
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
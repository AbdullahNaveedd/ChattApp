package com.example.chatapp.Auth.Message

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chatapp.R

class MessageAdapter(
    private val messageList: List<MessageDataClass>,
    private val onItemClick: (MessageDataClass) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.message_image)
        val userName: TextView = itemView.findViewById(R.id.message_name)
        val lastMessage: TextView = itemView.findViewById(R.id.message_description)
        val messageTime: TextView = itemView.findViewById(R.id.time)
        val notificationIcon: ImageView? = itemView.findViewById(R.id.message_notification)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val messageData = messageList[position]

        holder.userName.text = messageData.name
        holder.messageTime.text = messageData.time

        if (messageData.imageResId.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(messageData.imageResId)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.profile)
        }


        when (messageData.messageType) {
            "IMAGE" -> {
                holder.lastMessage.text = "📷 Photo"
            }
            "VOICE" -> {
                holder.lastMessage.text = "🎤 Voice Message"
            }
            "TEXT" -> {
                holder.lastMessage.text = messageData.description
            }
            else -> {
                holder.lastMessage.text = "[Unknown message]"
            }

        }
        if (!messageData.isRead) {
            holder.notificationIcon?.visibility = View.VISIBLE
        } else {
            holder.notificationIcon?.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(messageData)
        }
    }

    override fun getItemCount(): Int = messageList.size
}
package com.example.chatapp.Call

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.Fragements.UserChat
import com.example.chatapp.Fragements.Videocall
import com.example.chatapp.Fragements.VoiceCall
import com.example.chatapp.R

class CallAdapter(private val calls: List<CallDataClass>,
                  private val onCallClicked: (Boolean) -> Unit,
                  private val onitemClicked: (CallDataClass) -> Unit) :
    RecyclerView.Adapter<CallAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.call_image)
        val name: TextView = itemView.findViewById(R.id.call_name)
        val description: TextView = itemView.findViewById(R.id.call_description)
        val descriptionIcon: ImageView = itemView.findViewById(R.id.calldescriptionicon)
        val callicon: ImageView = itemView.findViewById(R.id.voicecall)
        val videocallicon: ImageView = itemView.findViewById(R.id.videcall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.call_layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = calls[position]

        holder.name.text = message.name
        holder.description.text = message.description ?: ""

        Glide.with(holder.itemView.context)
            .load(message.imageUrl ?: message.imageResId)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(holder.profileImage)
        holder.callicon.setOnClickListener{
          onCallClicked(true)
        }
        holder.videocallicon.setOnClickListener{
                onCallClicked(false)
        }
        holder.profileImage.setOnClickListener {
            onitemClicked(message)
        }
        holder.name.setOnClickListener {
            onitemClicked(message)
        }


        if (message.descriptionResId != null) {
            holder.descriptionIcon.visibility = View.VISIBLE
            holder.descriptionIcon.setImageResource(message.descriptionResId)
        } else {
            holder.descriptionIcon.visibility = View.GONE
        }

        if (message.callRedId != null) {
            holder.callicon.visibility = View.VISIBLE
            holder.callicon.setImageResource(message.callRedId)
        } else {
            holder.callicon.visibility = View.GONE
        }

        if (message.videoResId != null) {
            holder.videocallicon.visibility = View.VISIBLE
            holder.videocallicon.setImageResource(message.videoResId)
        } else {
            holder.videocallicon.visibility = View.GONE
        }

    }

    override fun getItemCount(): Int = calls.size
}

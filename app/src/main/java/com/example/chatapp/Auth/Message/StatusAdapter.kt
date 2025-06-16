package com.example.chatapp.Auth.Message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R

class StatusAdapter(private val statusList: List<Status>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SELF = 0
        private const val VIEW_TYPE_OTHER = 1
    }

    inner class SelfStatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.status_image)
        val name: TextView = itemView.findViewById(R.id.status_name)
        val background: ImageView = itemView.findViewById(R.id.background)
    }

    inner class OtherStatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.status_image)
        val name: TextView = itemView.findViewById(R.id.status_name)
        val background: ImageView = itemView.findViewById(R.id.background)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_SELF else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SELF) {
            val view = inflater.inflate(R.layout.status_item, parent, false)
            SelfStatusViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.status_item, parent, false)
            OtherStatusViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val status = statusList[position]

        if (holder is SelfStatusViewHolder) {
            holder.name.text = "My Status"
            holder.image.setImageResource(status.imageResId)
            holder.background.setImageResource(R.drawable.mystatus)
        } else if (holder is OtherStatusViewHolder) {
            holder.name.text = status.name
            holder.image.setImageResource(status.imageResId)
            holder.background.setImageResource(R.drawable.status_background)
        }
    }

    override fun getItemCount(): Int = statusList.size
}

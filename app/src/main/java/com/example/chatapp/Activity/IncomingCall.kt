package com.example.chatapp.Activity

import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.example.chatapp.Fragements.Videocall
import com.example.chatapp.Fragements.VoiceCall
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class IncomingCall : DialogFragment() {

    private var senderId: String? = null
    private var senderName: String? = null
    private var roomId: String? = null
    private var callType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            senderId = it.getString("sender_id")
            senderName = it.getString("sender_name")
            roomId = it.getString("room_id")
            callType = it.getString("call_type")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.incoming_call, null)

        val callerNameText = view.findViewById<TextView>(R.id.callerNameText)
        val callTypeText = view.findViewById<TextView>(R.id.callTypeText)
        val acceptButton = view.findViewById<Button>(R.id.acceptButton)
        val declineButton = view.findViewById<Button>(R.id.declineButton)

        callerNameText.text = senderName ?: "Unknown"
        callTypeText.text = if (callType == "video") "Video Call" else "Voice Call"

        acceptButton.setOnClickListener {
            acceptCall()
            dismiss()
        }

        declineButton.setOnClickListener {
            declineCall()
            dismiss()
        }

        builder.setView(view)
        val dialog = builder.create()

        // ✅ Make dialog non-cancelable and full screen
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    override fun onStart() {
        super.onStart()
        // ✅ Make dialog full width
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun acceptCall() {
        Log.d("CallDialog", "Call accepted")

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Cancel notification
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)

        // Start call fragment
        val bundle = Bundle().apply {
            putString("senderId", senderId)
            putString("receiverId", currentUserId)
            putString("roomId", roomId)
            putBoolean("isCallInitiator", false)
            putBoolean("isIncomingCall", true)
            putString("callType", callType)
        }

        val fragment = when (callType) {
            "video" -> Videocall().apply { arguments = bundle }
            else -> VoiceCall().apply { arguments = bundle }
        }

        (activity as? Fragement_Activity)?.supportFragmentManager?.beginTransaction()
            ?.replace(R.id.fragment_container_view, fragment)
            ?.commit()
    }

    private fun declineCall() {
        Log.d("CallDialog", "Call declined")

        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)

        roomId?.let { room ->
            val callRef = FirebaseDatabase.getInstance().reference.child("calls").child(room)
            callRef.child("status").setValue("declined")
        }
    }
}
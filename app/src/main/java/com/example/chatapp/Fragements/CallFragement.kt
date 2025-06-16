package com.example.chatapp.Fragements

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.Call.CallAdapter
import com.example.chatapp.Call.CallDataClass
import com.example.chatapp.R

class CallFragement : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_call_fragement, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViews = view.findViewById<RecyclerView>(R.id.callrecyclerview)
        recyclerViews.layoutManager = LinearLayoutManager(requireContext())

        val calllist = listOf(

            CallDataClass("Ahmed", "Today, 03:30 PM",R.drawable.callredicon,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Sara", "Today, 03:30 PM",R.drawable.calldescription,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),

            CallDataClass("Ahmed", "Today, 03:30 PM",R.drawable.callredicon,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Sara", "Today, 03:30 PM",R.drawable.calldescription,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Ahmed", "Today, 03:30 PM",R.drawable.callredicon,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Sara", "Today, 03:30 PM",R.drawable.calldescription,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Ahmed", "Today, 03:30 PM",R.drawable.callredicon,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
            CallDataClass("Sara", "Today, 03:30 PM",R.drawable.calldescription,
                R.drawable.profile.toString(), callRedId = R.drawable.call, videoResId =  R.drawable.video),
        )

        recyclerViews.adapter = CallAdapter(calllist,
            onCallClicked = { isVoiceCall ->
                val fragment = if (isVoiceCall) VoiceCall() else Videocall()
                val transaction = (requireActivity() as AppCompatActivity)
                    .supportFragmentManager.beginTransaction()
                transaction.replace(R.id.fragment_container_view, fragment)
                transaction.addToBackStack(null)
                transaction.commit()
            },
            onitemClicked = { selectedUser ->
                val transaction = (requireActivity() as AppCompatActivity)
                    .supportFragmentManager.beginTransaction()
                transaction.replace(R.id.fragment_container_view, UserChat())
                transaction.addToBackStack(null)
                transaction.commit()
            }
        )
    }
}
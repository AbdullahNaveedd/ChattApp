package com.example.chatapp.Fragements

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.Activity.Onboarding
import com.example.chatapp.Call.CallAdapter
import com.example.chatapp.Call.CallDataClass
import com.example.chatapp.R

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [Setting.newInstance] factory method to
 * create an instance of this fragment.
 */
class Setting : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var btnlogout:Button
    private  lateinit var backbtn:ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backbtn = view.findViewById(R.id.backbtn)
        btnlogout = view.findViewById(R.id.btnlogout)

        btnlogout.setOnClickListener{
            val sharedPref = requireContext().getSharedPreferences("auth_pref" ,AppCompatActivity.MODE_PRIVATE)
            sharedPref.edit().putBoolean("is_logged_in",false ).apply()

            val intent = Intent(requireActivity(), Onboarding::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        backbtn.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.homeframe, Message())
                .commit()
        }

        val recyclerViews = view.findViewById<RecyclerView>(R.id.callrecyclerview)
        recyclerViews.layoutManager = LinearLayoutManager(requireContext())

        val calllist = listOf(
            CallDataClass(name = "Account", imageUrl = R.drawable.account, description = "Privacy, security, change number"),
            CallDataClass(name = "Chat", imageUrl = R.drawable.chat,description = "Chat history,theme,wallpapers"),
            CallDataClass(name = "Notifications", imageUrl = R.drawable.notification, description = "Messages, group and others"),
            CallDataClass(name = "Help", imageUrl = R.drawable.help, description = "Help center,contact us, privacy policy"),
            CallDataClass(name = "Storage and data", imageUrl = R.drawable.storageanddata,description = "Network usage, stogare usage"),
            CallDataClass(name = "Invite a friend", imageUrl = R.drawable.invitefriend,description = "Invite friend"),

        )

        recyclerViews.adapter = CallAdapter(calllist,
            onCallClicked = { isVoiceCall ->
            },
            onitemClicked = { selectedUser ->
            }
        )
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Setting.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Setting().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
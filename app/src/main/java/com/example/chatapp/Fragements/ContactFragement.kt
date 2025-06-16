package com.example.chatapp.Fragements

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.Call.CallAdapter
import com.example.chatapp.Call.CallDataClass
import com.example.chatapp.Firebase.Users
import com.example.chatapp.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ContactFragement.newInstance] factory method to
 * create an instance of this fragment.
 */
class ContactFragement : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

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
        return inflater.inflate(R.layout.fragment_contact_fragement, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViews = view.findViewById<RecyclerView>(R.id.callrecyclerview)
        recyclerViews.layoutManager = LinearLayoutManager(requireContext())



        lifecycleScope.launchWhenStarted {
            fetchUsersForCalls().collect { callList ->
                recyclerViews.adapter = CallAdapter(
                    callList,
                    onCallClicked = { isVoiceCall ->
                        val fragment = if (isVoiceCall) VoiceCall() else Videocall()
                        val transaction =
                            (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
                        transaction.replace(R.id.fragment_container_view, fragment)
                        transaction.addToBackStack(null)
                        transaction.commit()
                    },
                    onitemClicked = { selectedUser ->
                        val userChatFragment = UserChat.newInstance(selectedUser.name,
                            selectedUser.imageResId.toString(),
                            receiverId = selectedUser.userId.toString()
                        )
                        val transaction = (requireActivity() as AppCompatActivity)
                            .supportFragmentManager.beginTransaction()
                        transaction.replace(R.id.fragment_container_view, userChatFragment)
                        transaction.addToBackStack(null)
                        transaction.commit()
                    }
                )
            }
        }
    }

    fun fetchUsersForCalls(): Flow<List<CallDataClass>> = flow {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("Users")
            .get()
            .await()

        val callList = snapshot.documents.mapNotNull { doc ->
            val user = doc.toObject(Users::class.java)
            user?.let {
                CallDataClass(
                    name = it.name ?: "Unknown",
                    imageResId = it.profilePicture ?: "",
                    userId = doc.id
                )
            }
        }.sortedBy { it.name.lowercase() }

        emit(callList)
    }
}
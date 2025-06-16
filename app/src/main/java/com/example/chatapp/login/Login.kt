package com.example.chatapp.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.example.chatapp.Activity.Onboarding
import com.example.chatapp.R
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import com.example.chatapp.Activity.Fragement_Activity

class Login : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var login: Button
    private lateinit var btn: Button
    private lateinit var progressbar: ProgressBar


    private lateinit var authViewModel: AuthViewModel

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_login)

        emailField = findViewById(R.id.emailtext)
        passwordField = findViewById(R.id.passwordtext)
        login = findViewById(R.id.login_btn)
        btn = findViewById(R.id.backbtn)
        progressbar = findViewById(R.id.progressbar)

        progressbar.visibility = View.GONE

        authViewModel = ViewModelProvider(
            this,
            object : Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(AuthRepository()) as T
                }
            }
        )[AuthViewModel::class.java]

        login.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                progressbar.visibility = View.VISIBLE
                authViewModel.loginUser(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        btn.setOnClickListener {
            val intent = Intent(this, Onboarding::class.java)
            startActivity(intent)
        }

        observeViewModel()
        setupInputWatcher()
    }

    private fun setupInputWatcher() {
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateInputs()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        emailField.addTextChangedListener(textWatcher)
        passwordField.addTextChangedListener(textWatcher)
    }

    private fun validateInputs() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            login.setBackgroundResource(R.drawable.btn_active)
            login.setTextColor(resources.getColor(R.color.white, theme))

        } else {
            login.setBackgroundResource(R.drawable.btn_not_active)
            login.setTextColor(resources.getColor(R.color.text, theme))

        }
    }
    private fun observeViewModel() {
        authViewModel.loginStatus.observe(this) { result ->
            progressbar.visibility = View.GONE

            result.fold(
                onSuccess = {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                val intent = Intent(this, Fragement_Activity::class.java)
                 startActivity(intent)
                    val sharedPref = getSharedPreferences("auth_pref", MODE_PRIVATE)
                    sharedPref.edit().putBoolean("is_logged_in", true).apply()

                    finish()
                },
                onFailure = {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

package com.example.chatapp.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.Factory
import com.example.chatapp.Activity.Onboarding
import com.example.chatapp.R
import com.google.android.material.textfield.TextInputLayout

class Signup : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private lateinit var authViewModel: AuthViewModel

    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var confirmPasswordField: EditText
    private lateinit var signUpButton: Button
    private lateinit var backButton: Button
    private lateinit var validemail: TextInputLayout
    private lateinit var validname: TextInputLayout
    private lateinit var validpassword: TextInputLayout
    private lateinit var validconfirmpassword: TextInputLayout
    private lateinit var progressbar: ProgressBar

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_signup)

        nameField = findViewById(R.id.nametext)
        emailField = findViewById(R.id.emailtexts)
        passwordField = findViewById(R.id.passwordtexts)
        confirmPasswordField = findViewById(R.id.confirmpasswordtext)
        signUpButton = findViewById(R.id.login)
        backButton = findViewById(R.id.backbtns)
        progressbar = findViewById(R.id.progressbars)
        validemail = findViewById(R.id.entervalidemail)
        validname = findViewById(R.id.entervalidname)
        validpassword = findViewById(R.id.entervalidpassword)
        validconfirmpassword = findViewById(R.id.entervalidconfirmpassword)

        progressbar.visibility = View.GONE
        validemail.visibility=View.GONE
        validname.visibility=View.GONE
        validpassword.visibility=View.GONE
        validconfirmpassword.visibility=View.GONE

        authViewModel = ViewModelProvider(
            this,
            object : Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(AuthRepository()) as T
                }
            }
        )[AuthViewModel::class.java]


        backButton.setOnClickListener {
            startActivity(Intent(this, Onboarding::class.java))
        }

        signUpButton.setOnClickListener {

            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val confirmPassword = confirmPasswordField.text.toString().trim()

            progressbar.visibility = View.VISIBLE
            authViewModel.registerUser(name, email, password, confirmPassword)

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

        nameField.addTextChangedListener(textWatcher)
        emailField.addTextChangedListener(textWatcher)
        passwordField.addTextChangedListener(textWatcher)
        confirmPasswordField.addTextChangedListener(textWatcher)
    }

    @SuppressLint("ResourceAsColor")
    private fun validateInputs() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            signUpButton.setBackgroundResource(R.drawable.btn_active)
            signUpButton.setTextColor(resources.getColor(R.color.white, theme))

        } else {
            signUpButton.setBackgroundResource(R.drawable.btn_not_active)
            signUpButton.setTextColor(resources.getColor(R.color.text, theme))


        }
    }


    private fun observeViewModel() {
        authViewModel.signupStatus.observe(this) { result ->
            progressbar.visibility = View.GONE

            Log.d("observeSignup", "Triggered: $result")
            result.onSuccess {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java))
                finish()
            }

            result.onFailure {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                validemail.visibility=View.VISIBLE
                validname.visibility=View.VISIBLE
                validpassword.visibility=View.VISIBLE
                validconfirmpassword.visibility=View.VISIBLE
            }
        }

        }
    }


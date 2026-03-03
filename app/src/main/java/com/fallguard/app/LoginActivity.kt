package com.fallguard.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

/**
 * LoginActivity — The Login Screen
 *
 * What it does:
 * 1. Shows email + password fields
 * 2. Authenticates with Firebase Auth
 * 3. Checks if email is verified
 * 4. If successful → opens MainActivity (monitoring starts)
 * 5. If no account → navigate to RegisterActivity
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var auth: FirebaseAuth

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var errorText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var registerLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // If user is already logged in AND email is verified, skip login screen
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            Log.d(TAG, "User already logged in: ${currentUser.email}")
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)

        // Find all UI elements
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        errorText = findViewById(R.id.errorText)
        loadingBar = findViewById(R.id.loadingBar)
        registerLink = findViewById(R.id.registerLink)

        // Set up login button
        loginButton.setOnClickListener {
            handleLogin()
        }

        // Set up register link
        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Handles the login process:
     * 1. Validate inputs
     * 2. Sign in with Firebase Auth
     * 3. Check if email is verified
     * 4. Navigate to main screen
     */
    private fun handleLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        // Validate inputs
        if (email.isEmpty()) {
            showError("Please enter your email")
            return
        }
        if (password.isEmpty()) {
            showError("Please enter your password")
            return
        }

        // Show loading, hide error
        setLoading(true)
        hideError()

        // Sign in with Firebase
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)

                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "Login successful: ${user?.email}")

                    // Check if email is verified
                    if (user != null && user.isEmailVerified) {
                        navigateToMain()
                    } else {
                        showError("Please verify your email first.\nCheck your inbox for the verification link.")
                        // Sign out since email is not verified
                        auth.signOut()
                    }
                } else {
                    Log.w(TAG, "Login failed: ${task.exception?.message}")
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "No account found with this email"
                        task.exception?.message?.contains("password is invalid") == true ->
                            "Incorrect password"
                        task.exception?.message?.contains("badly formatted") == true ->
                            "Invalid email format"
                        task.exception?.message?.contains("network") == true ->
                            "No internet connection"
                        else -> "Login failed: ${task.exception?.message}"
                    }
                    showError(errorMessage)
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorText.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        loginButton.alpha = if (loading) 0.5f else 1.0f
    }
}

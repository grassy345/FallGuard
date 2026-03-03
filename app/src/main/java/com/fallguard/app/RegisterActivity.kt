package com.fallguard.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * RegisterActivity — The Registration Screen
 *
 * What it does:
 * 1. Checks if the email is in the authorized_caregivers list in RTDB
 * 2. If authorized: creates account with Firebase Auth
 * 3. Sends email verification link
 * 4. User must click the link before they can log in
 *
 * Security flow:
 * Admin adds email to /authorized_caregivers/{encoded_email}: true
 * User enters email → app checks if it's in the list → creates account → verifies email
 * Only pre-approved caregivers can register!
 */
class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
    }

    private lateinit var auth: FirebaseAuth

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var registerButton: Button
    private lateinit var errorText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var loginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // Find all UI elements
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        errorText = findViewById(R.id.errorText)
        loadingBar = findViewById(R.id.loadingBar)
        loginLink = findViewById(R.id.loginLink)

        // Set up register button
        registerButton.setOnClickListener {
            handleRegister()
        }

        // Set up login link
        loginLink.setOnClickListener {
            finish()  // Go back to login screen
        }
    }

    /**
     * Handles the registration process:
     * 1. Validate inputs
     * 2. Check if email is in authorized_caregivers list
     * 3. Create account with Firebase Auth
     * 4. Send email verification
     */
    private fun handleRegister() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Validate inputs
        if (email.isEmpty()) {
            showError("Please enter your email")
            return
        }
        if (password.isEmpty()) {
            showError("Please enter a password")
            return
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }
        if (password != confirmPassword) {
            showError("Passwords do not match")
            return
        }

        setLoading(true)
        hideError()

        // Step 1: Check if email is in authorized_caregivers list
        checkAuthorization(email, password)
    }

    /**
     * Checks if the email is pre-approved in /authorized_caregivers in Firebase RTDB.
     * Emails are stored with dots replaced by commas (Firebase doesn't allow dots in keys).
     * Example: "john@gmail.com" → stored as "john@gmail,com"
     */
    private fun checkAuthorization(email: String, password: String) {
        val database = FirebaseDatabase.getInstance()
        val encodedEmail = email.replace(".", ",")  // Firebase doesn't allow dots in keys
        val authRef = database.getReference("authorized_caregivers").child(encodedEmail)

        authRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                // Email is authorized — proceed with account creation
                Log.d(TAG, "Email '$email' is authorized — creating account")
                createAccount(email, password)
            } else {
                // Email is NOT in the authorized list
                setLoading(false)
                showError("This email is not registered as an authorized caregiver.\n\nPlease contact your administrator to be added to the system.")
                Log.w(TAG, "Email '$email' is NOT in authorized_caregivers list")
            }
        }.addOnFailureListener { e ->
            setLoading(false)
            showError("Could not verify authorization.\nPlease check your internet connection.")
            Log.e(TAG, "Failed to check authorization: ${e.message}")
        }
    }

    /**
     * Creates a new Firebase Auth account and sends email verification.
     */
    private fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Account created for: $email")

                    // Send email verification link
                    sendVerificationEmail()
                } else {
                    setLoading(false)
                    Log.w(TAG, "Account creation failed: ${task.exception?.message}")
                    val errorMessage = when {
                        task.exception?.message?.contains("already in use") == true ->
                            "An account with this email already exists.\nTry logging in instead."
                        task.exception?.message?.contains("badly formatted") == true ->
                            "Invalid email format"
                        task.exception?.message?.contains("weak password") == true ->
                            "Password is too weak — use at least 6 characters"
                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    showError(errorMessage)
                }
            }
    }

    /**
     * Sends a verification email to the user.
     * They must click the link before they can log in.
     */
    private fun sendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                setLoading(false)

                if (task.isSuccessful) {
                    Log.d(TAG, "Verification email sent to: ${user.email}")

                    // Show success dialog
                    AlertDialog.Builder(this)
                        .setTitle("Verification Email Sent! ✉️")
                        .setMessage(
                            "A verification link has been sent to ${user.email}.\n\n" +
                            "Please check your inbox (and spam folder) and click the link to verify your email.\n\n" +
                            "After verifying, come back and log in."
                        )
                        .setPositiveButton("Go to Login") { _, _ ->
                            auth.signOut()  // Sign out until email is verified
                            finish()  // Go back to login screen
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    showError("Failed to send verification email.\nPlease try again.")
                    Log.e(TAG, "Failed to send verification: ${task.exception?.message}")
                }
            }
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
        registerButton.isEnabled = !loading
        registerButton.alpha = if (loading) 0.5f else 1.0f
    }
}

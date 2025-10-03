package com.jks.jatrav3

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AuthScreen : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auth_screen)
        val btnLogin = findViewById<LinearLayout>(R.id.btn_login)
        val btnSignup = findViewById<LinearLayout>(R.id.btn_signup)
        val btnBack = findViewById<ImageView>(R.id.image_frame_arrow_left)


        btnLogin.setOnClickListener {
            // Set first launch to false
//            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        btnSignup.setOnClickListener {
            // Set first launch to false
//            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
            startActivity(Intent(this, Signup::class.java))
            finish()
        }
        btnBack.setOnClickListener {
            startActivity(Intent(this, Onboarding4::class.java))
            finish()
        }

    }
}
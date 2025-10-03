package com.jks.jatrav3

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class Onboarding4 : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding4)

        val btnContinue = findViewById<Button>(R.id.btnLogin)
        val btnCreate = findViewById<TextView>(R.id.tvCreate)

        btnContinue.setOnClickListener {
            // Set first launch to false
//            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
            startActivity(Intent(this, Homescreen::class.java))
            finish()
        }
        btnCreate.setOnClickListener {
            // Set first launch to false
//            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
            startActivity(Intent(this, AuthScreen::class.java))
            finish()
        }

    }
}
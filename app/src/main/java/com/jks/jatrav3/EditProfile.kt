package com.jks.jatrav3

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jks.jatrav3.api.SessionManager

class EditProfile : AppCompatActivity() {
    private lateinit var session: SessionManager

    private lateinit var tvFullName: EditText           // displays current name inside the textviewbg box
    private lateinit var tvEmail: EditText
    private lateinit var tvFullname1 : TextView
    private lateinit var tvEmail1: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        session = SessionManager(this)
        val btnBack = findViewById<ImageView>(R.id.image_arrow_left)
        tvFullName = findViewById<EditText>(R.id.text_emily_sezehn1) // the name TextView inside textviewbg in XML
        tvEmail = findViewById<EditText>(R.id.text_user_email)
        tvFullname1 = findViewById<TextView>(R.id.text_emily_sezehn)
        tvEmail1 = findViewById<TextView>(R.id.text_email)
        loadProfileToUI()



        btnBack?.setOnClickListener {
            // Set first launch to false
            //            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
            finish()
        }
    }
    private fun loadProfileToUI() {
        val first = session.getFirstName().orEmpty()
        val last = session.getSurname().orEmpty()
        val space = " "
        val full = first+ space +last
        tvFullName.setText(full)
        tvEmail.setText(session.getEmail().orEmpty().ifEmpty { "user@example.com" })
        tvFullname1.text =full
        tvEmail1.text = session.getEmail().orEmpty().ifEmpty { "user@example.com" }
    }
}
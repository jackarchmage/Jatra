package com.jks.jatrav3

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.JatraApi
import com.jks.jatrav3.api.LoginOtpRequest
import com.jks.jatrav3.api.LoginOtpResponse
import com.jks.jatrav3.api.SessionManager
import com.jks.jatrav3.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding


    private val session: SessionManager by lazy { SessionManager(this) }

    // Retrofit API
    private val api: JatraApi by lazy { ApiClient.jatraApi }


    // track server-returned OTP and returned customer id / object
    private var serverOtp: String? = null
    private var returnedCustomerId: String? = null
    private var firstname: String? = null
    private var surname: String? = null
    private var email: String? = null
    private var number: String? = null



    // whether OTP was already requested (button becomes Verify)
    private var otpRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        val etPhone = findViewById<EditText>(R.id.etPhone)
//        val etOtp = findViewById<EditText>(R.id.etOtp)
//        val btnLogin = findViewById<Button>(R.id.btnLogin)

//        val session = SessionManager(this)

        binding.etOtp.isEnabled = false
        binding.btnLogin.text = "Get OTP"

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@LoginActivity, Homescreen::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }

        // Add the callback to the dispatcher; it will be lifecycle-aware
        onBackPressedDispatcher.addCallback(this, callback)

        binding.btnSignup.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            startActivity(intent)
        }

        binding.btnLogin.setOnClickListener {
            val phone = binding.etPhone.text?.toString().orEmpty().trim()
            if (!otpRequested) {
                // Step 1: request OTP (client generates random OTP and sends to server per your API)
                if (phone.isBlank()) {
                    Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                requestOtp(phone)
            } else {
                // Step 2: verify OTP entered by user
                val typedOtp = binding.etOtp.text?.toString().orEmpty().trim()
                if (typedOtp.isBlank()) {
                    Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                verifyOtpAndFinish(phone = binding.etPhone.text?.toString().orEmpty().trim(), typedOtp = typedOtp)
            }
        }
    }
    private fun requestOtp(phone: String) {
        // generate random 4-digit OTP
        val randomOtp = Random.nextInt(1000, 9999).toString()

        // disable UI while calling
        setUiEnabled(false)

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.loginOtp(LoginOtpRequest(c_number = phone, otp = randomOtp))
                }

                if (resp.isSuccessful) {
                    val body: LoginOtpResponse? = resp.body()
                    serverOtp = body?.otp
                    returnedCustomerId = body?.jatra_customer?._id
                    firstname = body?.jatra_customer?.f_name
                    surname = body?.jatra_customer?.s_name
                    email = body?.jatra_customer?.email
                    number = body?.jatra_customer?.contact
                    // Enable OTP input and change button
                    binding.etPhone.isEnabled = false
                    binding.etOtp.isEnabled = true
                    otpRequested = true
                    binding.btnLogin.text = "Verify OTP"

                    Toast.makeText(this@LoginActivity, "OTP requested. Check your SMS.", Toast.LENGTH_SHORT).show()

                    // Debug log (remove in production)
                    Log.d("LoginActivity", "Server returned OTP: $serverOtp, customer: $returnedCustomerId")
                } else {
                    val err = try { resp.errorBody()?.string() } catch (e: Exception) { null }
                    Toast.makeText(this@LoginActivity, "Request OTP failed: ${resp.code()} ${err ?: ""}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LoginActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                setUiEnabled(true)
            }
        }
    }

    private fun verifyOtpAndFinish(phone: String, typedOtp: String) {
        // If server returned OTP earlier, compare locally
        if (!serverOtp.isNullOrBlank()) {
            if (typedOtp == serverOtp) {
                // success — we already have returnedCustomerId (maybe)
                completeLogin(returnedCustomerId, phone, firstname,surname,email)
                return
            } else {
                Toast.makeText(this, "Incorrect OTP. Please try again.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // If server did not return OTP (production case), verify server-side by calling loginOtp with typed OTP
        setUiEnabled(false)
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.loginOtp(LoginOtpRequest(c_number = phone, otp = typedOtp))
                }

                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body?.message.equals("Success", ignoreCase = true) || !body?.jatra_customer?._id.isNullOrBlank()) {
                        completeLogin(body?.jatra_customer?._id, phone,firstname,surname,email)
                    } else {
                        Toast.makeText(this@LoginActivity, "OTP verification failed", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val err = try { resp.errorBody()?.string() } catch (e: Exception) { null }
                    Toast.makeText(this@LoginActivity, "Verify failed: ${resp.code()} ${err ?: ""}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LoginActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                setUiEnabled(true)
            }
        }
    }

    private fun completeLogin(
        customerId: String?, phone: String, firstname: String?, surname: String?,
        email: String?
    ) {
        if (!customerId.isNullOrBlank()) {
            session.saveCustomerId(customerId)
            session.saveCustomer(customerId,firstname,surname,email)
            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
            val data = Intent().apply { putExtra("customer_id", customerId) }
            setResult(Activity.RESULT_OK, data)
            val intent = Intent(this, Homescreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("customer_id", customerId)
            }
            startActivity(intent)
            finish()
            finish()
        } else {
            // As a last resort, try to fetch the customer by calling endpoint again with same OTP (handled above)
            Toast.makeText(this, "Login succeeded but customer id missing", Toast.LENGTH_LONG).show()
            // You can optionally finish with RESULT_CANCELED or keep user on screen
        }
    }


    private fun setUiEnabled(enabled: Boolean) {
        runOnUiThread {
            // phone only editable before OTP request
            binding.etPhone.isEnabled = enabled && !otpRequested
            binding.etOtp.isEnabled = enabled && otpRequested
            binding.btnLogin.isEnabled = enabled
        }
    }
}
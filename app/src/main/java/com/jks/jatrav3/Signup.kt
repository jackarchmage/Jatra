package com.jks.jatrav3

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.databinding.ActivitySignupBinding
import com.jks.jatrav3.api.JatraRepository
import com.jks.jatrav3.api.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import retrofit2.HttpException

class Signup : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private val repo = JatraRepository() // uses ApiClient.jatraApi by default

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.verificationBtn.setOnClickListener { submitForm() }

        binding.textPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitForm()
                true
            } else false
        }
        binding.imageArrowLeft.setOnClickListener {
            val nextIntent = Intent(this@Signup, com.jks.jatrav3.LoginActivity::class.java)
            nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(nextIntent)
            finish()
        }

    }
    private fun submitForm() {
        val req = RegisterRequest(
            f_name = binding.edtFirstname.text?.toString().orEmpty(),
            s_name = binding.edtSurname.text?.toString().orEmpty(),
            address = binding.edtAddress.text?.toString().orEmpty(),
            ad_line = binding.edtAdd2.text?.toString().orEmpty(),
            pincode = binding.edtPin.text?.toString().orEmpty(),
            city = binding.edtCity.text?.toString().orEmpty(),
            state = binding.edtState.text?.toString().orEmpty(),
            contact = binding.edtContact.text?.toString().orEmpty(),
            email = binding.edtEmail.text?.toString().orEmpty(),
            pwd = binding.edtPwd.text?.toString().orEmpty()
        )

        // Basic local validation
        if (req.f_name.isBlank() || req.email.isBlank() || req.pwd.isBlank()) {
            Toast.makeText(this, "Please enter name, email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // UI: show progress, disable button
//        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.verificationBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                // perform network call on IO dispatcher
                val response = withContext(Dispatchers.IO) {
                    repo.register(req)
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    val message = body?.message ?: "Registered successfully"
                    Toast.makeText(this@Signup, message, Toast.LENGTH_LONG).show()
                    Log.d("Signup", "Success response: $body")
                    if (message.equals("Success", ignoreCase = true)) {

//                        // Optionally you can pass returned user id/email via Intent extras
//                        val intent = Intent(this@Signup, Homescreen::class.java)
//
//                        // Example: pass created user id if available
//                        val createdId = body?.jatra_customer?._id
//                        if (!createdId.isNullOrBlank()) {
//                            intent.putExtra("created_user_id", createdId)
//                        }
//
//                        startActivity(intent)
//                        finish()
//                        LoginBottomSheet().show(supportFragmentManager, "ProjectBottomSheet")
//                          Auto-login: save created user id if returned by API
                        val createdId = body?.jatra_customer?._id

                        if (!createdId.isNullOrBlank()) {
                            // Persist session
                            val session = com.jks.jatrav3.api.SessionManager(this@Signup)
                            session.saveCustomerId(createdId)

                            // Optionally store other fields you want (email/contact) in prefs here

                            // Navigate to next screen (example: GetLoan)
                            val nextIntent = Intent(this@Signup, com.jks.jatrav3.Homescreen::class.java)
                            nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(nextIntent)
                            finish()
                        } else {
                            // Created id missing: still consider registration successful but alert and navigate to login or home
                            Toast.makeText(this@Signup, "Registered but server did not return user id. Please login.", Toast.LENGTH_LONG).show()

                            // Navigate to GetLoan or Login as you prefer:
                            val nextIntent = Intent(this@Signup, com.jks.jatrav3.Homescreen::class.java)
                            nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(nextIntent)
                            finish()
                        }
//                        LoginBottomSheet().show(supportFragmentManager, "LoginBottomSheet")

                        // Optional: navigate or finish
                        // finish()
                    }
                } else {
                    // read server error if available
                    val code = response.code()
                    val err = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    val errMsg = "Server returned $code. ${err ?: "No error body"}"
                    Toast.makeText(this@Signup, errMsg, Toast.LENGTH_LONG).show()
                    Log.w("SignupActivity", errMsg)
                }
            } catch (e: HttpException) {
                Log.e("SignupActivity", "HttpException", e)
                Toast.makeText(this@Signup, "Http error: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Log.e("SignupActivity", "Network error", e)
                Toast.makeText(this@Signup, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("SignupActivity", "Unexpected error", e)
                Toast.makeText(this@Signup, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                // restore UI
//                binding.progressBar.visibility = android.view.View.GONE
                binding.verificationBtn.isEnabled = true
            }
        }
    }
}
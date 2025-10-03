package com.jks.jatrav3

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.api.LoanTextData
import com.jks.jatrav3.api.SessionManager
import kotlinx.coroutines.launch
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.JatraApi
import kotlin.getValue

class GetLoan : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val api: JatraApi by lazy { ApiClient.retrofit.create(JatraApi::class.java) }

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Login succeeded. Optionally read returned customer id
            val customerId = result.data?.getStringExtra("customer_id")
            // session.saveCustomerId(customerId) // LoginActivity already saved it
            Toast.makeText(this, "Welcome — you are logged in", Toast.LENGTH_SHORT).show()
            // continue; UI already initialised
        } else {
            // Login canceled or failed — close this screen (or keep showing a message)
            Toast.makeText(this, "Login required to proceed", Toast.LENGTH_SHORT).show()
            finish() // close GetLoan if user didn't login
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_get_loan)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        session = SessionManager(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)


        // If not logged in, force login first
        if (!session.isLoggedIn()) {
            val loginIntent = Intent(this, LoginActivity::class.java)
            loginLauncher.launch(loginIntent)
            // return — we still keep the UI alive but user must login (onActivityResult handles continuing)
        } else {
            // already logged in -> fetch loan status right away
            val customerId = session.getCustomerId()
            if (!customerId.isNullOrBlank()) {
                fetchLoanStatus(customerId)
            }
            val cb1 = findViewById<CheckBox>(R.id.roadcheck1)
            val cb2 = findViewById<CheckBox>(R.id.roadcheck2)
            val cb3 = findViewById<CheckBox>(R.id.northchk)
            val cb4 = findViewById<CheckBox>(R.id.southchk)
            val cb5 = findViewById<CheckBox>(R.id.eastchk)
            val cb6 = findViewById<CheckBox>(R.id.westchk)
//            val cb7 = findViewById<CheckBox>(R.id.otherchk)
            val editLoan1 = findViewById<EditText>(R.id.editloan1)
            val editlength = findViewById<EditText>(R.id.editlength)
            val editbreadth = findViewById<EditText>(R.id.editbreadth)
            val btnNext = findViewById<Button>(R.id.btnNext)
            val dropdown = findViewById<Spinner>(R.id.dropdown1)
            val btnBack = findViewById<ImageView>(R.id.btn_back)
            val appdir1 = findViewById<CheckBox>(R.id.dirNorth)
            val appdir2 = findViewById<CheckBox>(R.id.dirSouth)
            val appdir3 = findViewById<CheckBox>(R.id.dirEast)
            val appdir4 = findViewById<CheckBox>(R.id.dirWest)
            val editAdditional = findViewById<EditText>(R.id.ed_additional)
            val scrollview = findViewById<ScrollView>(R.id.main)


            val items = listOf("Panchayat", "Municipals")

            val adapter = ArrayAdapter(
                this,
                com.jks.jatrav3.R.layout.spinner_item,
                items
            )
            editAdditional.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    scrollview.post {
                        scrollview.smoothScrollTo(0, editAdditional.top)
                    }
                }
            }

// Use dropdown layout style
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dropdown.adapter = adapter

// Handle selection
            dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    val selectedItem = items[position]
                    Toast.makeText(this@GetLoan, "Selected: $selectedItem", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Optional: handle if nothing selected
                }
            }

            btnNext.isEnabled = false


            val checkBoxes = listOf(cb1, cb2)
            val directionChkBox = listOf(cb3, cb4, cb5, cb6)
            val approachDirection = listOf(appdir1, appdir2, appdir3, appdir4)
            val lldirection = findViewById<LinearLayout>(R.id.ll_direction)
            val llns = findViewById<LinearLayout>(R.id.ll_ns)
            val llew = findViewById<LinearLayout>(R.id.ll_ew)

            btnBack.setOnClickListener { finish() }




            checkBoxes.forEach { cb ->
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        checkBoxes.filter { it != cb }.forEach { it.isChecked = false }
                        if (cb1.isChecked) {
                            lldirection.visibility = View.VISIBLE
                            llew.visibility = View.VISIBLE
                            llns.visibility = View.VISIBLE
                        } else {
                            lldirection.visibility = View.GONE
                            llew.visibility = View.GONE
                            llns.visibility = View.GONE
                        }
                    }
                    validateInputs(checkBoxes, directionChkBox, editLoan1, editlength, editbreadth,btnNext)
                }
            }



            directionChkBox.forEach { cb ->
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        directionChkBox.filter { it != cb }.forEach { it.isChecked = false }
                    }
                    validateInputs(checkBoxes, directionChkBox, editLoan1, editlength,editbreadth, btnNext)
                }
            }
            approachDirection.forEach { cb ->
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        approachDirection.filter { it != cb }.forEach { it.isChecked = false }
                    }
                }
            }

            editLoan1.addTextChangedListener {
                validateInputs(checkBoxes, directionChkBox, editLoan1, editlength, editbreadth,btnNext)
            }
            editlength.addTextChangedListener {
                validateInputs(checkBoxes, directionChkBox, editLoan1, editlength,editbreadth ,btnNext)
            }
            editbreadth.addTextChangedListener {
                validateInputs(checkBoxes, directionChkBox, editLoan1, editlength,editbreadth, btnNext)
            }
            btnNext?.setOnClickListener {
//            // Set first launch to false
//            //            SessionManager(this).isFirstLaunch = false
//
//            // Go to SplashActivity again (or directly MainActivity)
//            val intent = Intent(this, ActivityGetLoan2::class.java)
//            startActivity(intent)
                // Build values from current UI
                val selectedLocalBody = dropdown.selectedItem?.toString().orEmpty()

                // isApproach: if cb1 is checked => "yes", else "no"
                val isApproachValue = if (cb1.isChecked) "yes" else "no"
                val approachDirection = when{
                    appdir1.isChecked -> "North"
                    appdir2.isChecked -> "South"
                    appdir3.isChecked -> "East"
                    appdir4.isChecked -> "West"
                    else -> ""
                }

                // houseFacingDirection: find which direction checkbox is checked
                val houseFacing = when {
                    cb3.isChecked -> "North"
                    cb4.isChecked -> "South"
                    cb5.isChecked -> "East"
                    cb6.isChecked -> "West"
                    else -> ""
                }

                val length = editlength.text?.toString().orEmpty()
                val breadth = editbreadth.text?.toString().orEmpty()
                val loan = editLoan1.text?.toString().orEmpty()

                // Optional: extra guard (shouldn't happen because validateInputs enables the button)
                if (selectedLocalBody.isBlank() || length.isBlank() || breadth.isBlank() || houseFacing.isBlank()) {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val customerIdFromSession: String? =
                    session.getCustomerId() // should be non-null now
                if (customerIdFromSession.isNullOrBlank()) {
                    // Not logged in — launch login (extra safety)
                    val loginIntent = Intent(this, LoginActivity::class.java)
                    loginLauncher.launch(loginIntent)
                    return@setOnClickListener
                }

                // create the parcelable
                val loanData = LoanTextData(
                    customerId = customerIdFromSession,
                    localBodyType = selectedLocalBody,
                    isApproach = isApproachValue,
                    approachDirection = approachDirection,
                    houseFacingDirection = houseFacing,
                    propertyLength = length,
                    propertyBreadth = breadth,
                    loanAmount = loan
                )

                // start second activity and pass parcelable
                val intent = Intent(this, ActivityGetLoan2::class.java).apply {
                    putExtra("loan_text_data", loanData)
                }
                startActivity(intent)

            }


        }
    }
    private fun validateInputs(
        roadCheckBoxes: List<CheckBox>,
        directionCheckBoxes: List<CheckBox>,
        editText: EditText,
        editText2: EditText,
        editText3: EditText,
        nextBtn: Button
    ) {
        val roadSelected = roadCheckBoxes.any { it.isChecked }
        val directionSelected = directionCheckBoxes.any { it.isChecked }
        val textFilled = editText.text.toString().isNotBlank()
        val textFilled1 = editText2.text.toString().isNotBlank()
        val textFilled2 = editText3.text.toString().isNotBlank()

        nextBtn.isEnabled = roadSelected && directionSelected && textFilled && textFilled1 &&textFilled2
    }
    // Networking: fetch loan and show dialog if pending
    // -------------------------
    private fun fetchLoanStatus(customerId: String) {
        val progress = ProgressDialog(this)
        progress.setMessage("Checking loan status...")
        progress.setCancelable(false)
        progress.show()

        lifecycleScope.launch {
            try {
                val response = api.getLoanStatus(customerId)
                progress.dismiss()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.loanInfo != null) {
                        val info = body.loanInfo
                        val status = info.loanStatus?.lowercase() ?: ""
                        if (status == "pending") {
                            // show dialog with only loan id and amount
                            showPendingDialog(info.id ?: "-", info.loanAmount ?: "-")
                        } else {
                            // not pending -> brief toast with status
                            Toast.makeText(this@GetLoan, "Loan status: ${info.loanStatus}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@GetLoan, "No loan info returned", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@GetLoan, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@GetLoan, "Network error: ${e.localizedMessage ?: "Unknown"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPendingDialog(loanId: String, amount: String) {
        val message = "Loan ID: $loanId\nAmount: ₹$amount"
        AlertDialog.Builder(this)
            .setTitle("Loan Pending")
            .setMessage(message)
            .setPositiveButton("OK", { _, _ ->
                // Navigate to another page (e.g., LoanDetailsActivity)
                val intent = Intent(this, Homescreen::class.java)
                startActivity(intent)
            })
            .show()
    }
}
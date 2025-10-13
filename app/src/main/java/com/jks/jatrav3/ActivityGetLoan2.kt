package com.jks.jatrav3

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.JatraApi
import com.jks.jatrav3.api.LoanTextData
import com.jks.jatrav3.api.toPlainRequestBody
import com.jks.jatrav3.api.uriToMultipartPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import org.json.JSONObject
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class ActivityGetLoan2 : AppCompatActivity(), PaymentResultListener {

    // selected URIs
    private var giftUri: Uri? = null
    private var jamabandiUri: Uri? = null
    private var traceUri: Uri? = null
    private var imageUris: List<Uri> = emptyList()

    // pickers
    private val pickGift = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            giftUri = it
            Toast.makeText(this, "Gift deed selected", Toast.LENGTH_SHORT).show()
        }
    }
    private val pickJamabandi = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            jamabandiUri = it
            Toast.makeText(this, "Jamabandi selected", Toast.LENGTH_SHORT).show()
        }
    }
    private val pickTrace = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            traceUri = it
            Toast.makeText(this, "Trace map selected", Toast.LENGTH_SHORT).show()
        }
    }
    private val pickImages = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            imageUris = uris
            Toast.makeText(this, "Selected ${uris.size} image(s)", Toast.LENGTH_SHORT).show()
        }
    }

    // reuse your ApiClient retrofit instance

    private val loanApi: JatraApi by lazy { ApiClient.jatraApi }


    // keep references during payment flow
    private var pendingLoanData: LoanTextData? = null
    private var pendingSubmitView: View? = null
    private var pendingPickers: List<View> = emptyList()
    private var progressDialog: ProgressDialog? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_get_loan2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnSubmit = findViewById<Button>(R.id.btn_submit)
        val btnGiftDeed = findViewById<LinearLayout>(R.id.btn_giftdeed)
        val btnJamabandi = findViewById<LinearLayout>(R.id.btn_jamabandi)
        val btnTraceMap = findViewById<LinearLayout>(R.id.btn_tracemap)
        val btnPropertyPhoto = findViewById<LinearLayout>(R.id.btn_propertyphoto)

        val loanData = intent.getParcelableExtra<LoanTextData>("loan_text_data")
        if (loanData == null) {
            Toast.makeText(this, "Loan data missing — please fill the previous form again", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnBack?.setOnClickListener { finish() }

        btnGiftDeed.setOnClickListener { pickGift.launch(arrayOf("application/pdf")) }      // allow only PDFs
        btnJamabandi.setOnClickListener { pickJamabandi.launch(arrayOf("application/pdf")) }
        btnTraceMap.setOnClickListener { pickTrace.launch(arrayOf("application/pdf")) }
        btnPropertyPhoto.setOnClickListener { pickImages.launch(arrayOf("image/*")) }         // multiple images

        btnSubmit?.setOnClickListener {
            // Save context to continue after payment
            pendingLoanData = loanData
            pendingSubmitView = btnSubmit
            pendingPickers = listOf(btnGiftDeed, btnJamabandi, btnTraceMap, btnPropertyPhoto)

            // Fetch prepaid amount from server then start Razorpay checkout
            lifecycleScope.launch {
                progressDialog = ProgressDialog(this@ActivityGetLoan2).apply {
                    setMessage("Preparing payment...")
                    setCancelable(false)
                    show()
                }

                try {
                    val resp = loanApi.getLoanPrepaidAmount()
                    progressDialog?.dismiss()
                    if (resp.isSuccessful) {
                        val body = resp.body()
                        val first = body?.loan_prepaid_info?.firstOrNull()
                        val prepaidStr = first?.prepaid_value
                        if (!prepaidStr.isNullOrBlank()) {
                            val valueDouble = prepaidStr.toDoubleOrNull() ?: 0.0
                            if (valueDouble <= 0.0) {
                                Toast.makeText(this@ActivityGetLoan2, "Invalid prepaid amount returned", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val amountInPaise = (valueDouble * 100).toInt() // Razorpay expects integer paise
                            startRazorpayCheckout(amountInPaise, "Loan Processing Fee")
                        } else {
                            Toast.makeText(this@ActivityGetLoan2, "No prepaid amount found", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@ActivityGetLoan2, "Failed to fetch amount: ${resp.code()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    progressDialog?.dismiss()
                    Toast.makeText(this@ActivityGetLoan2, "Network error: ${e.localizedMessage ?: "Unknown"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -------------------------
    // Razorpay helpers
    // -------------------------
    private fun startRazorpayCheckout(amountInPaise: Int, description: String) {
        try {
            Checkout.preload(applicationContext)
            val co = Checkout()
            co.setKeyID("rzp_live_Oq2TmGYemF7HYF") // <-- REPLACE with your Razorpay key
            val options = JSONObject()
            options.put("name", "Jatra")
            options.put("description", description)
            options.put("currency", "INR")
            options.put("amount", amountInPaise) // paise

            // Prefill (optional) - set if you have user's contact/email
            val prefill = JSONObject()
            // prefill.put("email", "user@example.com")
            // prefill.put("contact", "9999999999")
            options.put("prefill", prefill)

            co.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Error in starting payment: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Payment callbacks
    override fun onPaymentSuccess(razorpayPaymentID: String?) {
        Toast.makeText(this, "Payment successful: $razorpayPaymentID", Toast.LENGTH_SHORT).show()
        // IMPORTANT: For production, verify payment on backend before uploading.
        // Here we proceed to upload on client-side after success.
        pendingLoanData?.let { data ->
            proceedToUpload(data, pendingSubmitView, pendingPickers, razorpayPaymentID)
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        Toast.makeText(this, "Payment failed or cancelled: $response (code $code)", Toast.LENGTH_LONG).show()
        // do not upload; user can retry
    }

    // -------------------------
    // Upload logic (moved into function)
    // -------------------------
    private fun proceedToUpload(
        loanData: LoanTextData,
        submitView: View?,
        pickers: List<View>,
        razorpayPaymentID: String?
    ) {
        setUploadingState(isUploading = true, submitView = submitView, pickers = pickers)
        progressDialog = ProgressDialog(this).apply {
            setMessage("Uploading — please wait...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val customerIdStr = loanData.customerId
                if (customerIdStr.isBlank()) {
                    Toast.makeText(this@ActivityGetLoan2, "Customer id missing (login required)", Toast.LENGTH_LONG).show()
                    setUploadingState(isUploading = false, submitView = submitView, pickers = pickers)
                    progressDialog?.dismiss()
                    return@launch
                }

                val localBody = loanData.localBodyType
                val loanAmount = loanData.loanAmount // if you added loanAmount to LoanTextData, use it here
                val propertyLength = loanData.propertyLength
                val propertyBreadth = loanData.propertyBreadth
//                val propertyJson = """{"length":"${loanData.propertyLength}","breadth":"${loanData.propertyBreadth}"}"""
                val isApproach = loanData.isApproach
                val approachDir = loanData.approachDirection
                val houseFacing = loanData.houseFacingDirection

                val customerPart = customerIdStr.toPlainRequestBody()
                val localBodyPart = localBody.toPlainRequestBody()
                val loanAmountPart = loanAmount.toPlainRequestBody()
//                val propertySizePart = propertyJson.toPlainRequestBody()
                val isApproachPart = isApproach.toPlainRequestBody()
                val loanLengthPart = propertyLength.toPlainRequestBody()
                val loanBreadthPart = propertyBreadth.toPlainRequestBody()
                val approachPart = approachDir.toPlainRequestBody()
                val houseFacingPart = houseFacing.toPlainRequestBody()

                // IMPORTANT: payment text parts (correct mapping)
                val paymentIdPart = (razorpayPaymentID ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                val paymentStatusPart = "success".toRequestBody("text/plain".toMediaTypeOrNull())


                val giftPart: MultipartBody.Part? = withContext(Dispatchers.IO) {
                    giftUri?.let { uriToMultipartPart(this@ActivityGetLoan2, "gift_deed", it) }
                }
                val jamabandiPart: MultipartBody.Part? = withContext(Dispatchers.IO) {
                    jamabandiUri?.let { uriToMultipartPart(this@ActivityGetLoan2, "jamabandi", it) }
                }
                val tracePart: MultipartBody.Part? = withContext(Dispatchers.IO) {
                    traceUri?.let { uriToMultipartPart(this@ActivityGetLoan2, "trace_map", it) }
                }
                val imageParts: List<MultipartBody.Part>? = withContext(Dispatchers.IO) {
                    imageUris.map { uri ->
                        uriToMultipartPart(this@ActivityGetLoan2, "property_image", uri)
                    }.takeIf { it.isNotEmpty() }
                }


                val response = withContext(Dispatchers.IO) {
                    loanApi.createLoanMultipart(
                        customer = customerPart,
                        localBodyType = localBodyPart,
                        loanAmount = loanAmountPart,
                        propertySizeLength = loanLengthPart,
                        propertySizeBreadth = loanBreadthPart,
                        isApproach = isApproachPart,
                        approachDirection = approachPart,
                        houseFacingDirection = houseFacingPart,
                        payment_id = paymentIdPart,        // exact name expected by server
                        payment_status = paymentStatusPart,//
                        gift_deed = giftPart,
                        jamabandi = jamabandiPart,
                        trace_map = tracePart,
                        property_image = imageParts
                        )
                }

                progressDialog?.dismiss()

                if (response.isSuccessful) {
                    Toast.makeText(this@ActivityGetLoan2, "Form submitted successfully!\n Will return to Home in 2s", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this@ActivityGetLoan2, Homescreen::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }, 2000L)
                } else {
                    val err = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    Toast.makeText(this@ActivityGetLoan2, "Server error: ${response.code()} ${err ?: ""}", Toast.LENGTH_LONG).show()
                    setUploadingState(isUploading = false, submitView = submitView, pickers = pickers)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                progressDialog?.dismiss()
                Toast.makeText(this@ActivityGetLoan2, "Upload error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                setUploadingState(isUploading = false, submitView = submitView, pickers = pickers)
            }
        }
    }

    private fun setUploadingState(isUploading: Boolean, submitView: View?, pickers: List<View>) {
        runOnUiThread {
            submitView?.isEnabled = !isUploading
            pickers.forEach { it.isEnabled = !isUploading }
            submitView?.alpha = if (isUploading) 0.6f else 1f
        }
    }
}

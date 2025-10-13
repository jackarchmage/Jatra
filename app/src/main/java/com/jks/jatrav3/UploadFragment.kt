package com.jks.jatrav3

import android.app.AlertDialog
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.PrepaidResponse
import com.jks.jatrav3.api.UploadResponse
import com.jks.jatrav3.api.CountingRequestBody
import com.jks.jatrav3.api.SessionManager
import com.razorpay.Checkout
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File

class UploadFragment : Fragment() {

    private var pickedUri: Uri? = null
    private var uploadJob: Job? = null

    private var pickedBlueprintUri: Uri? = null


    private val session: SessionManager by lazy { SessionManager(requireContext()) }


    // TODO: obtain real customer id from your session/auth manager
    private lateinit var customerId: String

    // Views
    private lateinit var backBtn: ImageView
    private lateinit var centerColumn: LinearLayout
    private lateinit var ctaContainer: LinearLayout
    private lateinit var ctaText: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var cancelBtn: Button

    private lateinit var selectBlueprintBtn: View
    private lateinit var blueprintNameText: TextView


    // Replace with your Razorpay key id (test/live accordingly)
    private val RAZORPAY_KEY_ID = "rzp_live_Oq2TmGYemF7HYF"

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) { /* provider may not allow persistable permission */ }

                pickedUri = uri
                val name = getFileName(uri) ?: "file"
                ctaText.text = "UPLOAD (Selected: $name)"
                Toast.makeText(requireContext(), "Selected: $name", Toast.LENGTH_SHORT).show()
            }
        }

    // Blueprint image picker (images only)
    private val pickBlueprintLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) { /* provider may not allow persistable permission */ }

                pickedBlueprintUri = uri
                val name = getFileName(uri) ?: "blueprint"
                Toast.makeText(requireContext(), "Selected blueprint: $name", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preload Razorpay
        Checkout.preload(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_upload, container, false)

        backBtn = view.findViewById(R.id.image_frame_arrow_left)
        centerColumn = view.findViewById(R.id.center_column)
        ctaContainer = view.findViewById(R.id.container_cta)
        ctaText = view.findViewById(R.id.text_next_step)
        progressIndicator = view.findViewById(R.id.upload_progress_indicator)
        cancelBtn = view.findViewById(R.id.upload_cancel_btn)
        customerId = session.getCustomerId().toString()

        selectBlueprintBtn = view.findViewById(R.id.btn_image_upload)


        backBtn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame1, Ar1())
                .commit()
        }
        selectBlueprintBtn.setOnClickListener {
            // images only
            pickBlueprintLauncher.launch(arrayOf("image/*"))
        }

        centerColumn.setOnClickListener {
            // many 3D files don't have stable mime types, so use broad filters
            pickFileLauncher.launch(arrayOf("model/*", "application/*", "*/ *", "*/*"))
        }

        ctaContainer.setOnClickListener {
            if (pickedUri == null) {
                Toast.makeText(requireContext(), "Please select a file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // start prepaid -> payment -> upload flow
            fetchPrepaidAmountAndStartPayment()
        }

        cancelBtn.setOnClickListener {
            uploadJob?.cancel()
            progressIndicator.visibility = View.GONE
            cancelBtn.visibility = View.GONE
            Toast.makeText(requireContext(), "Upload cancelled", Toast.LENGTH_SHORT).show()
        }

        // hide progress UI initially
        progressIndicator.visibility = View.GONE
        cancelBtn.visibility = View.GONE

        return view
    }

    /**
     * Step 1: call prepaid API to get the amount, then start Razorpay Checkout.
     */
    private fun fetchPrepaidAmountAndStartPayment() {
        lifecycleScope.launch {
            val progress = AlertDialog.Builder(requireContext())
                .setTitle("Preparing Payment")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create()
            progress.show()
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.jatraApi.getPrepaidAmount() }
                progress.dismiss()

                if (resp.isSuccessful) {
                    val body: PrepaidResponse? = resp.body()
                    val valueStr = body?.arPrepaidInfo?.firstOrNull()?.prepaidValue
                    if (valueStr.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "Invalid prepaid amount from server", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val amountPaise = try {
                        (valueStr.toDouble() * 100).toInt()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Invalid amount format", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    startRazorpayCheckout(amountPaise)
                } else {
                    Toast.makeText(requireContext(), "Failed to get prepaid amount: ${resp.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(requireContext(), "Error fetching amount: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    /**
     * Step 2: open Razorpay Checkout. Activity (Homescreen) will receive callbacks and must forward result
     * by calling onRazorpayPaymentSuccess / onRazorpayPaymentError on this fragment.
     */
    private fun startRazorpayCheckout(amountPaise: Int) {
        // basic validation
        if (amountPaise <= 0) {
            Toast.makeText(requireContext(), "Invalid payment amount", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val checkout = Checkout()
            checkout.setKeyID(RAZORPAY_KEY_ID)

            // build options carefully — include required keys
            val options = org.json.JSONObject()
            options.put("name", "Elysian AR")
            options.put("description", "AR File Upload Payment")
            options.put("currency", "INR")
            options.put("amount", amountPaise) // integer paise

            // OPTIONAL: if you use server-side Orders, prefer passing order_id instead of amount
            // options.put("order_id", "order_XXXXXXXX")

            // prefill (optional but useful)
            val prefill = org.json.JSONObject()
            prefill.put("email", session.getEmail() ?: "")
            options.put("prefill", prefill)

            // final guard: ensure options has amount or order_id
            if (!options.has("amount") && !options.has("order_id")) {
                Toast.makeText(requireContext(), "Payment configuration missing amount/order_id", Toast.LENGTH_LONG).show()
                return
            }

            // Try opening checkout — protect with try/catch to avoid SDK crashes bubbling up
            try {
                checkout.open(requireActivity(), options)
            } catch (sdkEx: Exception) {
                sdkEx.printStackTrace()
                Toast.makeText(requireContext(), "Failed to start payment: ${sdkEx.localizedMessage}", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Unexpected error preparing payment: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Called by hosting Activity (Homescreen) when Razorpay reports success.
     * Homescreen should forward to this method with the razorpayPaymentId.
     */
    fun onRazorpayPaymentSuccess(paymentId: String) {
        // start upload passing the payment id (p_id)
        Toast.makeText(requireContext(), "Payment success: $paymentId", Toast.LENGTH_SHORT).show()
        startUploadWithPaymentId(paymentId)
    }

    /**
     * Called by hosting Activity (Homescreen) when Razorpay reports an error.
     */
    fun onRazorpayPaymentError(code: Int, response: String?) {
        Toast.makeText(requireContext(), "Payment failed: $response", Toast.LENGTH_LONG).show()
    }

    /**
     * Step 3: Upload the selected file, including p_id and p_status as form-data.
     * Shows progress via CountingRequestBody -> updates LinearProgressIndicator.
     */
    private fun startUploadWithPaymentId(paymentId: String) {
        val uri = pickedUri ?: run {
            Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
            return
        }

        val blueprintUri = pickedBlueprintUri ?: run {
            Toast.makeText(requireContext(), "No blueprint selected", Toast.LENGTH_SHORT).show()
            return
        }

        progressIndicator.progress = 0
        progressIndicator.visibility = View.VISIBLE
        cancelBtn.visibility = View.VISIBLE

        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Uploading")
            .setMessage("Uploading file...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        uploadJob = lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { uriToFile(requireContext().contentResolver, uri) }
                val blueprintFile = withContext(Dispatchers.IO) { uriToFile(requireContext().contentResolver, blueprintUri) }

                if (file == null) {
                    progressDialog.dismiss()
                    progressIndicator.visibility = View.GONE
                    cancelBtn.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to read file", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (file == null || blueprintFile == null) {
                    progressDialog.dismiss()
                    progressIndicator.visibility = View.GONE
                    cancelBtn.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to read files", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val mime = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                val blueprintMime = requireContext().contentResolver.getType(blueprintUri) ?: "image/*"

                val countingBody = CountingRequestBody(
                    file = file,
                    contentType = mime,
                    listener = object : CountingRequestBody.ProgressListener {
                        override fun onProgress(bytesWritten: Long, contentLength: Long) {
                            val percent = if (contentLength > 0) ((bytesWritten * 100) / contentLength).toInt() else 0
                            requireActivity().runOnUiThread {
                                progressIndicator.progress = percent
                            }
                        }
                    }
                )

                val multipart = MultipartBody.Part.createFormData("ar_file", file.name, countingBody)
                // Blueprint as normal RequestBody
                val blueprintRequestBody: RequestBody = blueprintFile.asRequestBody(blueprintMime.toMediaType())
                val blueprintPart = MultipartBody.Part.createFormData("blue_print", blueprintFile.name, blueprintRequestBody)

                val textMediaType = "text/plain".toMediaType()
                val cIdBody = customerId.toRequestBody(textMediaType)
                val pStatusBody = "success".toRequestBody(textMediaType)
                val pIdBody = paymentId.toRequestBody(textMediaType)

                // call your jatra upload api (ApiClient.jatraApi should have uploadArFile defined)
                val response = ApiClient.jatraUploadApi.uploadArFile(cIdBody, multipart, blueprintPart,pStatusBody, pIdBody)

                progressDialog.dismiss()
                progressIndicator.visibility = View.GONE
                cancelBtn.visibility = View.GONE

                if (response.isSuccessful) {
                    val body: UploadResponse? = response.body()
                    Toast.makeText(requireContext(), "Uploaded: ${body?.message ?: "Success"}", Toast.LENGTH_LONG).show()
                    // use the returned file_link if needed
                    val fileLink = body?.superAR?.file_link
                    if (!fileLink.isNullOrEmpty()) {
                        // handle link (show to user, navigate, etc.)
                    }
                    // reset UI
                    ctaText.text = "UPLOAD"
                    pickedUri = null
                } else {
                    Toast.makeText(requireContext(), "Upload failed: ${response.code()} ${response.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: HttpException) {
                progressDialog.dismiss()
                progressIndicator.visibility = View.GONE
                cancelBtn.visibility = View.GONE
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                progressDialog.dismiss()
                progressIndicator.visibility = View.GONE
                cancelBtn.visibility = View.GONE
                if (e is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(requireContext(), "Upload cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            } finally {
                uploadJob = null
            }
        }
    }

    // --- helpers: uri to file, filename, copy stream ---

    private fun uriToFile(contentResolver: ContentResolver, uri: Uri): File? {
        return try {
            val fileNameRaw = getFileName(uri) ?: "upload_file"
            // sanitize filename to avoid path components
            val fileName = fileNameRaw.replace(Regex("[:\\\\/\\s]+"), "_")
            val outFile = File(requireContext().cacheDir, fileName)

            // overwrite if exists
            if (outFile.exists()) outFile.delete()

            contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun getFileName(uri: Uri): String? {
        val doc = DocumentFile.fromSingleUri(requireContext(), uri)
        if (doc != null && !doc.name.isNullOrEmpty()) return doc.name

        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = it.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uploadJob?.cancel()
    }
}

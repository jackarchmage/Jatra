package com.jks.jatrav3

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.SessionManager
import com.jks.jatrav3.api.uriToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import androidx.core.net.toUri

class ProfileFragment : Fragment() {

    // initialize in onAttach to guarantee a valid Context
    private lateinit var session: SessionManager
    private lateinit var ttName: TextView
    private lateinit var ttEmail: TextView
    private lateinit var btnProfileImage: ImageView

    // Launcher to pick an image from gallery
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            btnProfileImage.setImageURI(it) // quick preview
            // start upload
            uploadProfileImage(it)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // safe to create SessionManager here (context is available)
        session = SessionManager(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isUserLoggedIn()) {
            redirectToLogin()
        }
    }

    @Suppress("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Views (non-null because layout is inflated)
        val btnMyProfile = view.findViewById<LinearLayout>(R.id.btn_profile)
        val btnOrder = view.findViewById<LinearLayout>(R.id.btnOrders)
        val btnNotification = view.findViewById<LinearLayout>(R.id.btnNotification)
        val btnSecurity = view.findViewById<LinearLayout>(R.id.btn_security)
        val btnHelp = view.findViewById<LinearLayout>(R.id.btn_helpsupport)
        val btnLogout = view.findViewById<LinearLayout>(R.id.btn_logout)
        val btnAbout = view.findViewById<LinearLayout>(R.id.btn_about)
        btnProfileImage = view.findViewById<ImageView>(R.id.btn_profile_photo)
        ttName = view.findViewById(R.id.tt_name)
        ttEmail = view.findViewById(R.id.tt_email)

        // Load saved info into UI
        refreshUI()

        btnMyProfile.setOnClickListener {
            val intent = Intent(requireContext(), EditProfile::class.java)
            startActivity(intent)
        }

        btnOrder.setOnClickListener {
            loadFragment(OrdersFragment())
        }

        btnAbout.setOnClickListener {
            loadFragment(AboutFragment())
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout") { _, _ ->
                    performLogout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnNotification.setOnClickListener { /* ... */ }
        btnSecurity.setOnClickListener {
            loadFragment(PrivacyFragment())
        }
        btnHelp.setOnClickListener { /* ... */ }

        // Click profile image to pick new photo
        btnProfileImage.setOnClickListener {
            // Launch gallery picker (only images)
            pickImageLauncher.launch("image/*")
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun performLogout() {
        try {
            session.clearSession()

            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()

            refreshUI()

            val intent = Intent(requireContext(), Homescreen::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            requireActivity().finish()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Logout failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun refreshUI() {
        val first = session.getFirstName()?.trim().orEmpty()
        val last = session.getSurname()?.trim().orEmpty()
        val displayName = when {
            first.isNotEmpty() && last.isNotEmpty() -> "$first $last"
            first.isNotEmpty() -> first
            last.isNotEmpty() -> last
            else -> "User"
        }
        // Update views if available
        if (this::ttName.isInitialized) ttName.text = displayName
        if (this::ttEmail.isInitialized) ttEmail.text = session.getEmail().orEmpty()

        // Optionally load remote thumbnail if available in session (example)
        val thumb = session.getThumbnail() // implement this in SessionManager if not present
        if (!thumb.isNullOrEmpty()) {
            // best to use an image loader (Glide/Coil/Picasso). Quick fallback:
             btnProfileImage.setImageURI(thumb.toUri())
//             Glide.with(this).load(thumb).into(btnProfileImage)
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return !session.getEmail().isNullOrEmpty()
    }

    private fun redirectToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    // Upload image to server (background)
    private fun uploadProfileImage(imageUri: Uri) {
        // Show a quick toast while upload starts
        Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // 1) Convert uri -> file (do IO on background thread)
                val file = withContext(Dispatchers.IO) {
                    // uriToFile is the helper that copies contentResolver stream to cache file.
                    // Implemented earlier; example signature: fun uriToFile(context: Context, uri: Uri, fileName: String): File
                    uriToFile(requireContext(), imageUri, "profile_${System.currentTimeMillis()}.jpg")
                }

                // 2) Prepare Multipart parts
                val uIdValue = session.getCustomerId() ?: session.getEmail().orEmpty()
                val uIdReqBody = RequestBody.create("text/plain".toMediaTypeOrNull(), uIdValue)

                val mime = requireContext().contentResolver.getType(imageUri) ?: "image/*"
                val requestFile = RequestBody.create(mime.toMediaTypeOrNull(), file)
                val body = MultipartBody.Part.createFormData("user_image", file.name, requestFile)

                // 3) Call Retrofit API (suspend function)
                val response = withContext(Dispatchers.IO) {
                    ApiClient.jatraApi.uploadUserImage(uIdReqBody, body)
                }

                if (response.isSuccessful && response.body() != null) {
                    val respBody = response.body()!!
                    // Example final image URL: image_path + thumbnail
                    val finalUrl = (respBody.image_path ?: "").let { base ->
                        val thumb = respBody.updated_user?.thumbnail ?: ""
                        if (base.isNotEmpty() && thumb.isNotEmpty()) base + thumb else ""
                    }

                    // Update SessionManager with the new thumbnail (if you maintain it in session)
                    session.saveThumbnail(respBody.updated_user?.thumbnail ?: "")

                    // Switch to Main thread for UI updates
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()

                        // If server returns a full URL or path, load it; otherwise, we already set preview earlier.
                        if (finalUrl.isNotEmpty()) {
                            // recommended: use Glide/Coil here. Quick fallback:
                            // btnProfileImage.setImageURI(Uri.parse(finalUrl))
                            // Better:
                            // Glide.with(this@ProfileFragment).load(finalUrl).into(btnProfileImage)
                        }

                        // Update name/email UI from updated_user if present
                        respBody.updated_user?.let { u ->
                            // Update fields in Session and UI
                            session.saveCustomer(u._id,u.f_name,u.s_name,u.email)
                            refreshUI()
                        }
                    }
                } else {
                    val errorText = response.errorBody()?.string()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Upload failed: ${response.code()} ${errorText.orEmpty()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Upload error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

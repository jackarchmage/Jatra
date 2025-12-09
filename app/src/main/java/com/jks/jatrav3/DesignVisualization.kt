package com.jks.jatrav3

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.jks.jatrav3.api.ApiClient
import com.jks.jatrav3.api.DesignItem
import com.jks.jatrav3.api.SessionManager
import com.jks.jatrav3.databinding.FragmentDesignVisualizationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesignVisualization : Fragment() {

    private var _binding: FragmentDesignVisualizationBinding? = null
    private val binding get() = _binding!!
    private val session: SessionManager by lazy { SessionManager(requireContext()) }


    // Adjust if your server stores images in a different folder
    private val imageBaseURL = "https://elysianarco.com/jatra/uploads/"

    private var injectedWebView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDesignVisualizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnQuotation.setOnClickListener {
            Toast.makeText(requireContext(), "GET QUOTATION clicked", Toast.LENGTH_SHORT).show()
            getQuotation()
        }

        // Back handler — adjust as needed
        binding.btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame1, DesignFragment())
                .commit()
        }

        fetchDesigns()
    }

    private fun fetchDesigns() {
        lifecycleScope.launch {
            showLoader()
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.designService.getVisualizations()
                }
                if (response.isSuccessful) {
                    val designs = response.body()?.all_designs.orEmpty()
                    if (designs.isNotEmpty()) {
                        showDesigns(designs)
                    } else {
                        showError("No designs found")
                    }
                } else {
                    showError("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Network error: ${e.localizedMessage}")
            }
            finally {
                hideLoader()
            }
        }
    }
    private fun getQuotation() {
        lifecycleScope.launch {
            val cId = session.getCustomerId()
            if (cId == null) {
                showError("Customer id is missing")
                return@launch
            }
            showLoader()
            try {
                val response = withContext(Dispatchers.IO) {
                    // ensure this matches your Retrofit method signature below
                    ApiClient.designService.getQuotation(cId)
                }

                if (response.isSuccessful) {
                    // optional: inspect body
                    val body = response.body()
                    // show success dialog on main thread (we're already on main after withContext)
                    AlertDialog.Builder(requireContext())
                        .setTitle("Success")
                        .setMessage("You will be notified shortly")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // read server error body for debugging
                    val errorText = try {
                        response.errorBody()?.string() ?: "No error body"
                    } catch (ex: Exception) {
                        "Failed to read errorBody: ${ex.localizedMessage}"
                    }
                    showError("Server error: ${response.code()} - $errorText")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Network error: ${e.localizedMessage}")
            }
            finally {
                hideLoader()
            }
        }
    }


    /**
     * Renders first two designs:
     * - First item -> image (iv_visual) + tv_visual1
     * - Second item -> webview (video) + tv_visual2
     */
    private fun showDesigns(list: List<DesignItem>) {
        // Clean up any old webview first
        removeInjectedWebView()

        // 1) First item (index 0) - prefer image, else fallback to link or placeholder
        val first = list.getOrNull(0)
        if (first != null) {
            binding.tvVisual1.text = first.description.orEmpty()

            if (!first.image.isNullOrBlank()) {
                binding.ivVisual.visibility = View.VISIBLE
                binding.videoVisual.visibility = View.GONE
                val url = first.image
                Glide.with(requireContext()).load(url).into(binding.ivVisual)
            } else if (!first.link.isNullOrBlank()) {
                // if first item has no image but has link, show it in a webview placed where image is expected
                binding.ivVisual.visibility = View.GONE
                binding.videoVisual.visibility = View.GONE
                injectWebViewForLink(first.link)
            } else {
                // no image/link -> show a placeholder (keep videoVisual as placeholder)
                binding.ivVisual.visibility = View.GONE
                binding.videoVisual.visibility = View.VISIBLE
                val mc = MediaController(requireContext())
                mc.setAnchorView(binding.videoVisual)
                binding.videoVisual.setMediaController(mc)
            }
        } else {
            binding.tvVisual1.text = ""
            binding.ivVisual.visibility = View.GONE
            binding.videoVisual.visibility = View.GONE
        }

        // 2) Second item (index 1) - prefer link (video), else show image (reuse iv_visual or place below)
        val second = list.getOrNull(1)
        if (second != null) {
            binding.tvVisual2.text = second.description.orEmpty()

            if (!second.link.isNullOrBlank()) {
                // ensure we have the image (first) shown above; now inject webview for second item
                injectWebViewForLink(second.link)
            } else if (!second.image.isNullOrBlank()) {
                // If second only has image, show it in the iv_visual (if first didn't use it) or replace with a second image.
                // Simpler approach: if first used iv_visual, we'll replace it with second image so second image shows in main visual slot.
                val imageUrl = second.image
                binding.ivVisual.visibility = View.VISIBLE
                binding.videoVisual.visibility = View.GONE
                Glide.with(requireContext()).load(imageUrl).into(binding.ivVisual)
            } else {
                // nothing for second -> hide web view and leave only first content
            }
        } else {
            binding.tvVisual2.text = ""
        }
    }

    /**
     * Injects a WebView containing an iframe for the provided link.
     * The WebView is inserted right after the VideoView in view hierarchy so it appears in the second slot.
     * Converts YouTube watch/short links to embed URL when possible.
     */
    private fun injectWebViewForLink(link: String) {
        val embedUrl = toEmbedYouTubeUrl(link) ?: link

        val webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 250).toInt()
            )
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
        }
        injectedWebView = webView

        val html = """
            <html>
              <body style="margin:0;padding:0;">
                <iframe width="100%" height="100%" src="$embedUrl" frameborder="0"
                 allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                 allowfullscreen></iframe>
              </body>
            </html>
        """.trimIndent()

        // Insert the webview right after videoVisual for predictable placement
        val parent = binding.contentLayout as ViewGroup
        val index = parent.indexOfChild(binding.videoVisual)
        if (index >= 0) {
            parent.addView(webView, index + 1)
        } else {
            parent.addView(webView)
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun toEmbedYouTubeUrl(url: String): String? {
        return try {
            when {
                url.contains("youtube.com/watch") -> {
                    val id = url.substringAfter("v=").substringBefore("&")
                    "https://www.youtube.com/embed/$id?rel=0&autoplay=1"
                }
                url.contains("youtu.be/") -> {
                    val id = url.substringAfterLast("/").substringBefore("?")
                    "https://www.youtube.com/embed/$id?rel=0&autoplay=1"
                }
                url.contains("youtube.com/embed/") -> url
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun removeInjectedWebView() {
        injectedWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            try {
                webView.stopLoading()
            } catch (_: Exception) { }
            // Instead of null, assign a new empty client
            webView.webViewClient = WebViewClient()
            try {
                webView.destroy()
            } catch (_: Exception) { }
            injectedWebView = null
        }
    }


    private fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeInjectedWebView()
        _binding = null
    }

    private fun showLoader() {
        try {
            binding.loaderOverlay.visibility = View.VISIBLE
        } catch (e: Exception) {
            // safe fallback in case binding not ready
        }
    }

    private fun hideLoader() {
        try {
            binding.loaderOverlay.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
        } catch (e: Exception) {
        }
    }
}

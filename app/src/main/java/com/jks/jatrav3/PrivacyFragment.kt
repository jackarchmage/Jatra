package com.jks.jatrav3

import android.os.Bundle
import android.text.Html
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.api.ApiClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PrivacyFragment : Fragment() {

    private var txtPrivacyContent: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_privacy, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // find the TextView from your XML
        txtPrivacyContent = view.findViewById(R.id.txtPrivacyContent)

        // start fetching the about text
        fetchPrivacy()
    }

    private fun fetchPrivacy() {
        // handle coroutine exceptions centrally
        val handler = CoroutineExceptionHandler { _, throwable ->
            // update UI on main thread when an exception occurs
            lifecycleScope.launch(Dispatchers.Main) {
                txtPrivacyContent?.text = "Failed to load content."
            }
        }

        // use viewLifecycleOwner.lifecycleScope so the coroutine cancels if view is destroyed
        viewLifecycleOwner.lifecycleScope.launch(handler) {
            // show a temporary loading text
            withContext(Dispatchers.Main) {
                txtPrivacyContent?.text = "Loading..."
            }

            val response = try {
                ApiClient.jatraApi.getPrivacy()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtPrivacyContent?.text = "Unable to reach server."
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val aboutList = body?.privacy_jatra
                    if (!aboutList.isNullOrEmpty()) {
                        val details = aboutList[0].details ?: "No details available."
                        // render HTML if present; otherwise plain text
                        txtPrivacyContent?.text = Html.fromHtml(details, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        txtPrivacyContent?.text = "No Privacy information found."
                    }
                } else {
                    txtPrivacyContent?.text = "Server error: ${response.code()}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // avoid memory leaks
        txtPrivacyContent = null
    }


}
package com.jks.jatrav3

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jks.jatrav3.api.ApiClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutFragment : Fragment() {

    private var txtAboutContent: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep for any non-UI setup if needed later
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment (your provided fragment_about.xml)
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // find the TextView from your XML
        txtAboutContent = view.findViewById(R.id.txtAboutContent)

        // start fetching the about text
        fetchAbout()
    }

    private fun fetchAbout() {
        // handle coroutine exceptions centrally
        val handler = CoroutineExceptionHandler { _, throwable ->
            // update UI on main thread when an exception occurs
            lifecycleScope.launch(Dispatchers.Main) {
                txtAboutContent?.text = "Failed to load content."
            }
        }

        // use viewLifecycleOwner.lifecycleScope so the coroutine cancels if view is destroyed
        viewLifecycleOwner.lifecycleScope.launch(handler) {
            // show a temporary loading text
            withContext(Dispatchers.Main) {
                txtAboutContent?.text = "Loading..."
            }

            val response = try {
                ApiClient.jatraApi.getAbout()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtAboutContent?.text = "Unable to reach server."
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val aboutList = body?.about_jatra
                    if (!aboutList.isNullOrEmpty()) {
                        val details = aboutList[0].details ?: "No details available."
                        // render HTML if present; otherwise plain text
                        txtAboutContent?.text = Html.fromHtml(details, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        txtAboutContent?.text = "No about information found."
                    }
                } else {
                    txtAboutContent?.text = "Server error: ${response.code()}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // avoid memory leaks
        txtAboutContent = null
    }
}

package com.jks.jatrav3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ARListActivity : AppCompatActivity() {

    private val viewModel: ARViewModel by viewModels()
    private lateinit var adapter: ARAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_arlist)

        // Edge-to-edge padding (keeps your original behavior)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Views (make sure these IDs exist in activity_arlist.xml)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val progressBar = findViewById<ProgressBar>(R.id.progress)
        val emptyView =
            findViewById<TextView>(R.id.emptyView) // optional textview to show "No files"
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressed() } // or finish()

        val fabUpload = findViewById<FloatingActionButton>(R.id.fab_upload)
        fabUpload.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame1, UploadFragment(), "upload_fragment_tag")
                .addToBackStack("upload_fragment")
                .commit()
        }
        // Adapter with click handler: open file_link in external browser
        adapter = ARAdapter { item ->
            val link = item.file_link
            if (!link.isNullOrBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No file link available", Toast.LENGTH_SHORT).show()
            }
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Observe ViewModel state
        viewModel.state.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    recycler.visibility = View.GONE
                    emptyView?.visibility = View.GONE
                }

                is UiState.Success -> {
                    progressBar.visibility = View.GONE
                    if (state.items.isNullOrEmpty()) {
                        recycler.visibility = View.GONE
                        emptyView?.visibility = View.VISIBLE
                        emptyView?.text = "No AR files available"
                    } else {
                        emptyView?.visibility = View.GONE
                        recycler.visibility = View.VISIBLE
                        adapter.submitList(state.items)
                    }
                }

                is UiState.Error -> {
                    progressBar.visibility = View.GONE
                    recycler.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                    emptyView?.text = "Error loading files"
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Kick off initial load with customer id from intent or fallback
        val customerId = intent.getStringExtra("customer_id") ?: "68c3e993bc713587f108660a"
        viewModel.loadArFiles(customerId)
    }

    /**
     * Keep this method here so it compiles with your earlier call to enableEdgeToEdge()
     * If you have a different implementation of enableEdgeToEdge(), remove this stub.
     */
    private fun enableEdgeToEdge() {

    }
}
// If you already have an extension function

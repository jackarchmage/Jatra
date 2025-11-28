package com.jks.jatrav3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jks.jatrav3.api.SessionManager
import com.jks.jatrav3.api.SuperArUser
import kotlinx.coroutines.launch

class ArListFragment : Fragment() {

    private val viewModel: ARViewModel by viewModels() // use activityViewModels() if you share the VM

    private lateinit var toolbar: Toolbar
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fab: FloatingActionButton

    // SessionManager should only be constructed after fragment is attached.
    // lazy { SessionManager(requireContext()) } is OK as long as we don't call it before onViewCreated.
    private val session: SessionManager by lazy { SessionManager(requireContext()) }

    private val adapter by lazy {
        ARAdapter { item -> openArFile(item) }
    }

    // will be initialized in onViewCreated from SessionManager
    private lateinit var customerIdArg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // nothing else here
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_ar_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        swipe = view.findViewById(R.id.swipe)
        recycler = view.findViewById(R.id.recycler)
        emptyView = view.findViewById(R.id.emptyView)
        progress = view.findViewById(R.id.progress)
        fab = view.findViewById(R.id.fab_upload)

        // Read customer id from session here (safe - context available)
        customerIdArg = session.getCustomerId()?.toString() ?: run {
            // fallback id if session returns null
            session.getCustomerId().toString()
        }

        // toolbar back — pop backstack (works when fragment is on backstack)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        swipe.setOnRefreshListener { loadList() }

        fab.setOnClickListener {
            // open UploadFragment inside host container (frame1)
            // guard so we don't crash if container id not present in host layout
            val containerExists = (activity?.findViewById<View?>(R.id.frame1) != null)
            if (containerExists) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.frame1, UploadFragment(), "upload_fragment_tag")
                    .addToBackStack("upload_fragment")
                    .commit()
            } else {
                // fallback: try to open UploadFragment in this fragment's parent container
                try {
                    parentFragmentManager.beginTransaction()
                        .replace(id, UploadFragment(), "upload_fragment_tag")
                        .addToBackStack("upload_fragment")
                        .commit()
                } catch (e: Exception) {
                    // final fallback: open external Homescreen (or show toast)
                    Toast.makeText(requireContext(), "Unable to open upload screen", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe ViewModel state
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    showLoading(true)
                    recycler.visibility = View.GONE
                    emptyView.visibility = View.GONE
                }
                is UiState.Success -> {
                    showLoading(false)
                    val items = state.items ?: emptyList()
                    if (items.isEmpty()) {
                        recycler.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = "No AR files available"
                    } else {
                        emptyView.visibility = View.GONE
                        recycler.visibility = View.VISIBLE
                        adapter.submitList(items)
                    }
                    swipe.isRefreshing = false
                }
                is UiState.Error -> {
                    showLoading(false)
                    recycler.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "Error loading files"
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    swipe.isRefreshing = false
                }
                else -> {
                    // unknown state — hide loading
                    showLoading(false)
                    swipe.isRefreshing = false
                }
            }
        }

        // trigger initial load after observer attached
        loadList()
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openArFile(item: SuperArUser) {
        val link = item.file_link
        val blueprint = item.blue_print
        val textlink = item.text_link
        if (!link.isNullOrBlank()) {
            try {
                // Launch VRView and pass the model URL via intent extra
                val intent = android.content.Intent(requireContext(), WebviewActivity::class.java)
                intent.putExtra("MODEL_URL", link)
                intent.putExtra("BLUE_PRINT_URL",blueprint)
                intent.putExtra("TEXT_LINK",textlink)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Unable to open AR viewer", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "No file link available", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadList() {
        // show UI while loading
        swipe.isRefreshing = true
        showLoading(true)

        // trigger ViewModel to load data (observer already updates UI)
        lifecycleScope.launch {
            try {
                viewModel.loadArFiles(customerIdArg)
            } catch (e: Exception) {
                e.printStackTrace()
                // The ViewModel should emit UiState.Error which the observer will handle,
                // but show a simple fallback here too:
                swipe.isRefreshing = false
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to start loading: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}

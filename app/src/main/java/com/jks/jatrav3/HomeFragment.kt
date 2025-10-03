package com.jks.jatrav3

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.jks.jatrav3.api.SessionManager

class HomeFragment : Fragment() {

    private lateinit var session: SessionManager
    private var tvHello: TextView? = null   // nullable

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // initialize session here so requireContext()/requireActivity() are safe later
        session = SessionManager(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }




    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val btnContinue = view.findViewById<FrameLayout>(R.id.getloan_btn)
        val btnDesignPage = view.findViewById<FrameLayout>(R.id.design_btn)
        val btnMaterials = view.findViewById<FrameLayout>(R.id.material_btn)
        val btnWorkers = view.findViewById<FrameLayout>(R.id.workers_btn)

        // greeting textview
        val tvHello = view.findViewById<TextView>(R.id.text_hello_there)

        // read user info from session manager (methods may return null)
        val first = session.getFirstName()?.trim().orEmpty()
        val last = session.getSurname()?.trim().orEmpty()

        val displayName = when {
            first.isNotEmpty() && last.isNotEmpty() -> "$first $last"
            first.isNotEmpty() -> first
            last.isNotEmpty() -> last
            else -> "Guest"
        }

        // set greeting; keep the comma consistent with your design
        tvHello.text = "Hello $displayName,"

        // button handlers
        btnContinue.setOnClickListener {
            // if user not logged in, you may want to force login
            if (session.getCustomerId().isNullOrBlank()) {
                // open LoginActivity and return (or show login bottom sheet)
                val intent = Intent(requireContext(), LoginActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(requireContext(), GetLoan::class.java)
                startActivity(intent)
            }
        }

        btnDesignPage.setOnClickListener {
            loadFragment(DesignFragment())
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.nav_design  // menu item id for "Design"
        }
        btnWorkers.setOnClickListener {
            loadFragment(WorkersFragment())
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.nav_hire  // menu item id for "Workers"
        }
        btnMaterials.setOnClickListener {
            loadFragment(MaterialsFragment())
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.nav_source  // menu item id for "Materials"
        }

        return view
    }
    override fun onResume() {
        super.onResume()
        // update when fragment resumes (visible again)
        updateGreeting()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // release reference to avoid memory leak
        tvHello = null
    }
    @SuppressLint("SetTextI18n")
    private fun updateGreeting() {
        val tvHello = view?.findViewById<TextView>(R.id.text_hello_there)
        val first = session.getFirstName()?.trim().orEmpty()
        val last = session.getSurname()?.trim().orEmpty()
        val displayName = when {
            first.isNotEmpty() && last.isNotEmpty() -> "$first $last"
            first.isNotEmpty() -> first
            last.isNotEmpty() -> last
            else -> "Guest"
        }
        tvHello?.text = "Hello $displayName,"
    }

    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .addToBackStack(null)
            .commit()
    }
}

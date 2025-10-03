package com.jks.jatrav3

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class DesignFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_design, container, false)
        val btnNewDesign = view?.findViewById<LinearLayout>(R.id.buildnewdesignbtn)
        val btnVisualization = view?.findViewById<LinearLayout>(R.id.designvisualizationbtn)
        val btnViewDesign = view?.findViewById<LinearLayout>(R.id.viewdesignbtn)

        btnViewDesign?.setOnClickListener {
//            val intent = Intent(requireContext(), VRView::class.java)
//            startActivity(intent)
            loadFragment(Ar1())
        }

        btnNewDesign?.setOnClickListener {
            // Set first launch to false
            //            SessionManager(this).isFirstLaunch = false

            // Go to SplashActivity again (or directly MainActivity)
//            val intent = Intent(requireContext(), GetLoan::class.java)
//            startActivity(intent)
//            MyBottomSheet().show(supportFragmentManager, "MyBottomSheet")

            val dialogView = layoutInflater.inflate(R.layout.dialog_underconstruction, null)

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .create()
                .show()
//            ProjectBottomSheet().show(parentFragmentManager, "ProjectBottomSheet")
        }
        btnVisualization?.setOnClickListener {
            loadFragment(DesignVisualization())
        }
        // Inflate the layout for this fragment
        return view
    }
    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .commit()
    }


}
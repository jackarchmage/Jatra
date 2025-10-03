package com.jks.jatrav3

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class ProjectBottomSheet : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_project_bottom_sheet, container, false)
        val btnResidential = view?.findViewById<ImageView>(R.id.residential_img)
        btnResidential?.setOnClickListener {

            val intent = Intent(requireContext(), ResidentialScreen::class.java)
            startActivity(intent)

        }
        // Inflate the layout for this fragment
        return view
    }

}
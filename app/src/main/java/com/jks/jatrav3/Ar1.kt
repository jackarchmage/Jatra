package com.jks.jatrav3

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout


class Ar1 : Fragment() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ar1, container, false)

        val btnVisualize = view.findViewById<LinearLayout>(R.id.btn_visualize)
        val btnUpload = view.findViewById<LinearLayout>(R.id.btn_upload)
        val btnBack = view.findViewById<ImageView>(R.id.image_frame_arrow_left)

        btnUpload.setOnClickListener {
            loadFragment(UploadFragment())
        }
        btnBack.setOnClickListener {
            loadFragment(DesignFragment())
        }
        btnVisualize.setOnClickListener {
            loadFragment(ArListFragment())
        }
        // Inflate the layout for this fragment
        return view
    }
    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .addToBackStack(null)
            .commit()
    }


}
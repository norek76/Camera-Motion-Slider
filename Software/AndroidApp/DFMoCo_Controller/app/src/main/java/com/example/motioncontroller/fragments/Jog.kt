package com.example.motioncontroller.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.motioncontroller.CustomMode
import com.example.motioncontroller.MainPage
import com.example.motioncontroller.R

class Jog : Fragment() {
    private lateinit var customModeActivity: CustomMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customModeActivity = activity as CustomMode
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_jog, container, false)

    }
}
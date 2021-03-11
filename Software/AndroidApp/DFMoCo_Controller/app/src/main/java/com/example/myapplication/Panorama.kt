package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment

class Panorama : Fragment() {
    lateinit var rowImages_et: EditText
    lateinit var rowSteps_et: EditText
    lateinit var rowMotor_et: EditText
    lateinit var columnImages_et: EditText
    lateinit var columnSteps_et: EditText
    lateinit var columnMotor_et: EditText
    lateinit var exposureTime_et: EditText
    lateinit var restTime_et: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_panorama, container, false)
        v.findViewById<Button>(R.id.timelapseGo_bt).setOnLongClickListener {
            view -> onClick(view)
            true
        }
        rowImages_et = v.findViewById<EditText>(R.id.rowImages_et)
        rowSteps_et = v.findViewById<EditText>(R.id.interval_et)
        rowMotor_et = v.findViewById<EditText>(R.id.rowMotor_et)
        columnImages_et = v.findViewById<EditText>(R.id.columnImages_et)
        columnSteps_et = v.findViewById<EditText>(R.id.columnSteps_et)
        columnMotor_et = v.findViewById<EditText>(R.id.columnMotor_et)
        exposureTime_et = v.findViewById<EditText>(R.id.exposureTime_et)
        restTime_et = v.findViewById<EditText>(R.id.restTime_et)

        return v
    }

    fun onClick(v: View) {
        when(v.id) {
            R.id.timelapseGo_bt -> {
                val rowImages = rowImages_et.text.toString()
                val rowSteps = rowSteps_et.text.toString()
                val rowMotor = rowMotor_et.text.toString()
                val columnImages = columnImages_et.text.toString()
                val columnSteps = columnSteps_et.text.toString()
                val columnMotor = columnMotor_et.text.toString()
                val exposureTime = exposureTime_et.text.toString()
                val restTime = restTime_et.text.toString()

                (activity as MainMenu)?.sendCommand("pa $rowMotor $rowImages $rowSteps $columnMotor $columnImages $columnSteps $exposureTime $restTime")
            }
        }
    }
}
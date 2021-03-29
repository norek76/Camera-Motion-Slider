package com.example.myapplication

import android.content.Context
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

    override fun onDetach() {
        super.onDetach()

        val prefEditor = activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.edit()
        prefEditor?.putString("pn_rowImages_et", rowImages_et?.text.toString())
        prefEditor?.putString("pn_rowSteps_et", rowSteps_et?.text.toString())
        prefEditor?.putString("pn_rowMotor_et", rowMotor_et?.text.toString())
        prefEditor?.putString("pn_columnImages_et", columnImages_et?.text.toString())
        prefEditor?.putString("pn_columnSteps_et", columnSteps_et?.text.toString())
        prefEditor?.putString("pn_columnMotor_et", columnMotor_et?.text.toString())
        prefEditor?.putString("pn_exposureTime_et", exposureTime_et?.text.toString())
        prefEditor?.putString("pn_restTime_et", restTime_et?.text.toString())
        prefEditor?.commit()
    }

    override fun onDestroy() {
        super.onDestroy()

        val prefEditor = activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.edit()
        prefEditor?.putString("pn_rowImages_et", rowImages_et?.text.toString())
        prefEditor?.putString("pn_rowSteps_et", rowSteps_et?.text.toString())
        prefEditor?.putString("pn_rowMotor_et", rowMotor_et?.text.toString())
        prefEditor?.putString("pn_columnImages_et", columnImages_et?.text.toString())
        prefEditor?.putString("pn_columnSteps_et", columnSteps_et?.text.toString())
        prefEditor?.putString("pn_columnMotor_et", columnMotor_et?.text.toString())
        prefEditor?.putString("pn_exposureTime_et", exposureTime_et?.text.toString())
        prefEditor?.putString("pn_restTime_et", restTime_et?.text.toString())
        prefEditor?.commit()
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

        v.findViewById<Button>(R.id.timelapseReset_bt).setOnLongClickListener {
            rowImages_et.setText("12")
            rowSteps_et.setText("533")
            rowMotor_et.setText("2")
            columnImages_et.setText("4")
            columnSteps_et.setText("-735")
            columnMotor_et.setText("3")
            exposureTime_et.setText("3000")
            restTime_et.setText("2000");

            true
        }

        rowImages_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_rowImages_et", "12") ?: "12")
        rowSteps_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_rowSteps_et", "533") ?: "533")
        rowMotor_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_rowMotor_et", "2") ?: "2")
        columnImages_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_columnImages_et", "4") ?: "4")
        columnSteps_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_columnSteps_et", "-735") ?: "-735")
        columnMotor_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_columnMotor_et", "3") ?: "3")
        exposureTime_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_exposureTime_et", "3000") ?: "3000")
        restTime_et.setText(activity?.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.getString("pn_restTime_et", "2000") ?: "2000")

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
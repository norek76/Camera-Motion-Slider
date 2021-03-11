package com.example.myapplication

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch

/**
 * A simple [Fragment] subclass.
 * Use the [Timelapse.newInstance] factory method to
 * create an instance of this fragment.
 */
class Timelapse : Fragment() {
    lateinit var images_et: EditText
    lateinit var interval_et: EditText
    lateinit var exposureTime_et: EditText
    lateinit var restTime_et: EditText
    lateinit var m1Start_et: EditText
    lateinit var m1End_et: EditText
    lateinit var motor1_sw: Switch
    lateinit var m2Start_et: EditText
    lateinit var m2End_et: EditText
    lateinit var motor2_sw: Switch
    lateinit var m3Start_et: EditText
    lateinit var m3End_et: EditText
    lateinit var motor3_sw: Switch
    lateinit var m4Start_et: EditText
    lateinit var m4End_et: EditText
    lateinit var motor4_sw: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_timelapse, container, false)
        v.findViewById<Button>(R.id.timelapseGo_bt).setOnLongClickListener {
            view -> onClick(view)
            true
        }
        images_et = v.findViewById<EditText>(R.id.images_et)
        interval_et = v.findViewById<EditText>(R.id.interval_et)
        exposureTime_et = v.findViewById<EditText>(R.id.exposureTime_et)
        restTime_et = v.findViewById<EditText>(R.id.restTime_et)
        motor1_sw = v.findViewById<Switch>(R.id.motor1_sw)
        m1Start_et = v.findViewById<EditText>(R.id.m1Start_et)
        m1End_et = v.findViewById<EditText>(R.id.m1End_et)
        motor2_sw = v.findViewById<Switch>(R.id.motor2_sw)
        m2Start_et = v.findViewById<EditText>(R.id.m2Start_et)
        m2End_et = v.findViewById<EditText>(R.id.m2End_et)
        motor3_sw = v.findViewById<Switch>(R.id.motor3_sw)
        m3Start_et = v.findViewById<EditText>(R.id.m3Start_et)
        m3End_et = v.findViewById<EditText>(R.id.m3End_et)
        motor4_sw = v.findViewById<Switch>(R.id.motor4_sw)
        m4Start_et = v.findViewById<EditText>(R.id.m4Start_et)
        m4End_et = v.findViewById<EditText>(R.id.m4End_et)

        m1Start_et.setOnLongClickListener {
            m1Start_et.setText((activity as MainMenu)?.getMotorPos(1));
            true
        }
        m1End_et.setOnLongClickListener {
            m1End_et.setText((activity as MainMenu)?.getMotorPos(1));
            true
        }
        m2Start_et.setOnLongClickListener {
            m2Start_et.setText((activity as MainMenu)?.getMotorPos(2));
            true
        }
        m2End_et.setOnLongClickListener {
            m2End_et.setText((activity as MainMenu)?.getMotorPos(2));
            true
        }
        m3Start_et.setOnLongClickListener {
            m3Start_et.setText((activity as MainMenu)?.getMotorPos(3));
            true
        }
        m3End_et.setOnLongClickListener {
            m3End_et.setText((activity as MainMenu)?.getMotorPos(3));
            true
        }
        m4Start_et.setOnLongClickListener {
            m4Start_et.setText((activity as MainMenu)?.getMotorPos(4));
            true
        }
        m4End_et.setOnLongClickListener {
            m4End_et.setText((activity as MainMenu)?.getMotorPos(4));
            true
        }

        return v
    }

    fun onClick(v: View) {
        when(v.id) {
            R.id.timelapseGo_bt -> {
                var command = "tl"
                val images = images_et.text.toString()
                val interval = interval_et.text.toString()
                val exposureTime = exposureTime_et.text.toString()
                val restTime = restTime_et.text.toString()

                command = command + " $images $interval $exposureTime $restTime"

                if (motor1_sw.isChecked) {
                    val start = m1Start_et.text.toString()
                    val end = m1End_et.text.toString()
                    command = command + " 1 $start $end"
                }
                if (motor2_sw.isChecked) {
                    val start = m2Start_et.text.toString()
                    val end = m2End_et.text.toString()
                    command = command + " 2 $start $end"
                }
                if (motor3_sw.isChecked) {
                    val start = m3Start_et.text.toString()
                    val end = m3End_et.text.toString()
                    command = command + " 3 $start $end"
                }
                if (motor4_sw.isChecked) {
                    val start = m4Start_et.text.toString()
                    val end = m4End_et.text.toString()
                    command = command + " 4 $start $end"
                }

                (activity as MainMenu)?.sendCommand(command)
            }
        }
    }
}
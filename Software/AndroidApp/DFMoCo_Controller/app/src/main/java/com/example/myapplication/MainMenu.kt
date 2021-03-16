package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.*
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.BluetoothService.Companion.EXTRA_BLUETOOTH_DATA
import com.example.myapplication.BluetoothService.Companion.EXTRA_BLUETOOTH_MESSAGE
import kotlinx.android.synthetic.main.activity_main_menu.*
import java.io.IOException


class MainMenu : AppCompatActivity() {
    var jogMode: JogMode? = null

    companion object {
        private var mHandler: Handler? = null
        lateinit var progress: ProgressDialog
        var isConnected: Boolean = false
        var address: String? = null
        lateinit var mService: BluetoothService
        var mBound: Boolean = false
        var activeMotor = 0;
        var jogModeActive = 0;
        var motorPos = arrayListOf<String>()
        var motorSpeed = arrayListOf<String>()
        var motorAcc = arrayListOf<String>()
    }

    public fun getMotorPos(motorNumber: Int): String {
        return motorPos[motorNumber - 1];
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)
        address = intent.getStringExtra(ConnectMenu.EXTRA_ADDRESS)
        activeMotor = 0;
        motorPos.add("0")
        motorPos.add("0")
        motorPos.add("0")
        motorPos.add("0")
        motorSpeed.add("0")
        motorSpeed.add("0")
        motorSpeed.add("0")
        motorSpeed.add("0")
        motorAcc.add("0")
        motorAcc.add("0")
        motorAcc.add("0")
        motorAcc.add("0")

        motorAllStop_bt.setOnClickListener {
            if (mBound) {
                mService.sendCommand("sa")
            }
        }

        disconnect_bt.setOnLongClickListener {
            disconnectBluetoothDevice()

            true
        }

        motor1_ib.setOnClickListener {
            activeMotor = 1;
            motor1_ib.setImageResource(R.drawable.ic_motor_active)
            motor2_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor3_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor4_ib.setImageResource(R.drawable.ic_motor_inactive)
        }

        motor2_ib.setOnClickListener {
            activeMotor = 2;
            motor1_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor2_ib.setImageResource(R.drawable.ic_motor_active)
            motor3_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor4_ib.setImageResource(R.drawable.ic_motor_inactive)
        }

        motor3_ib.setOnClickListener {
            activeMotor = 3;
            motor1_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor2_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor3_ib.setImageResource(R.drawable.ic_motor_active)
            motor4_ib.setImageResource(R.drawable.ic_motor_inactive)
        }

        motor4_ib.setOnClickListener {
            activeMotor = 4;
            motor1_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor2_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor3_ib.setImageResource(R.drawable.ic_motor_inactive)
            motor4_ib.setImageResource(R.drawable.ic_motor_active)
        }

        motor1_pos_tv.setOnLongClickListener {
            if (mBound) {
                mService.sendCommand("zm 1")
            }

            true
        }

        motor2_pos_tv.setOnLongClickListener {
            if (mBound) {
                mService.sendCommand("zm 2")
            }

            true
        }

        motor3_pos_tv.setOnLongClickListener {
            if (mBound) {
                mService.sendCommand("zm 3")
            }

            true
        }

        motor4_pos_tv.setOnLongClickListener {
            if (mBound) {
                mService.sendCommand("zm 4")
            }

            true
        }

        jogPos_bt.setOnTouchListener { v, m ->
            jogMode = JogMode("jm", "150000");
            jogModeEvent(v, m)
        }

        jogNeg_bt.setOnTouchListener { v, m ->
            jogMode = JogMode("jm", "-150000");
            jogModeEvent(v, m)
        }

        inchPos_bt.setOnTouchListener { v, m ->
            jogMode = JogMode("im", "150000");
            jogModeEvent(v, m)
        }

        inchNeg_bt.setOnTouchListener { v, m ->
            jogMode = JogMode("im", "-150000");
            jogModeEvent(v, m)
        }


        pano_bt.setOnClickListener {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.mode_fl, Panorama())
                .commit()
        }

        timelapse_bt.setOnClickListener {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.mode_fl, Timelapse())
                .commit()
        }

        modeStatus_bt.setOnClickListener {
            if (mBound) {
                mService.sendCommand("cm")
            }
        }

        modeReset_bt.setOnLongClickListener {
            if (mBound) {
                mService.sendCommand("cr")
            }

            true
        }

        motor1_settings_ib.setOnClickListener {
            createSettingsAlert(1, motorSpeed[0], motorAcc[0]).show()
        }

        motor2_settings_ib.setOnClickListener {
            createSettingsAlert(2, motorSpeed[1], motorAcc[1]).show()
        }

        motor3_settings_ib.setOnClickListener {
            createSettingsAlert(3, motorSpeed[2], motorAcc[2]).show()
        }

        motor4_settings_ib.setOnClickListener {
            createSettingsAlert(4, motorSpeed[3], motorAcc[3]).show()
        }
    }

    fun createSettingsAlert(motorNumber: Int, speed: String, acc: String): AlertDialog.Builder {
        var alert = AlertDialog.Builder(this);

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        var editSpeed = EditText(this)
        var editAcc = EditText(this)
        alert.setMessage("Update the 1. speed and 2. acc")
        alert.setTitle("Settings")

        layout.addView(editSpeed)
        layout.addView(editAcc)

        alert.setView(layout)

        editSpeed.setText(speed)
        editAcc.setText(acc)

        alert.setPositiveButton("Update", DialogInterface.OnClickListener { dialog, whichButton -> //What ever you want to do with the value
            val newSpeed = editSpeed.text.toString().trim()
            sendCommand("ve $motorNumber $newSpeed")
            val newAcc = editAcc.text.toString().trim()
            sendCommand("ac $motorNumber $newAcc")
        })

        return alert
    }

    fun jogModeEvent(v: View, m: MotionEvent): Boolean {
        when (m.getAction()) {
            MotionEvent.ACTION_DOWN -> {
                if (jogModeActive === 0) {
                    jogModeActive = 2;
                    if (mHandler != null || jogMode == null) {
                        mHandler!!.removeCallbacks(jogMode!!)
                        mHandler = null
                    }
                    mHandler = Handler()
                    jogMode!!.run()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (jogModeActive === 2) {
                    jogModeActive = 1
                    Handler().postDelayed({
                        jogModeActive = 0
                        mService.sendCommand("sm $activeMotor")
                    }, 200)
                }
                if (mHandler == null || jogMode == null) return true
                mHandler!!.removeCallbacks(jogMode!!)
                mHandler = null
                v.performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (jogModeActive === 2) {
                    jogModeActive = 1
                    Handler().postDelayed({
                        jogModeActive = 0
                        mService.sendCommand("sm $activeMotor")
                    }, 200)
                }
                if (mHandler == null || jogMode == null) return true
                mHandler!!.removeCallbacks(jogMode!!)
                mHandler = null
            }
        }
        return true
    }

    class JogMode(cmd_i: String, position_i: String) : Runnable {
        var cmd: String = ""
        var position: String= ""

        override fun run() {
            if (mBound && activeMotor > 0 && jogModeActive === 2) {
                mService.sendCommand("$cmd $activeMotor $position")
            }
            if (mHandler != null) {
                mHandler!!.postDelayed(this, 300)
            }
        }

        init {
            cmd = cmd_i
            position = position_i
        }
    }

    public fun sendCommand(input: String) {
        if (mBound) {
            mService.sendCommand("$input")
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, BluetoothService::class.java).also { intent -> bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE
        ) }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageReceiver, IntentFilter(EXTRA_BLUETOOTH_DATA))
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.BluetoothBinder
            mService = binder.getService()
            mBound = true
            connectBluetoothDevice();
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val msg = intent.getStringExtra(EXTRA_BLUETOOTH_MESSAGE)
            if (msg != null && msg != "") {
                Log.i("data", msg)
                val msgParts = msg.split(" ");
                Log.i("data", msgParts[0])
                Log.i("data", (msgParts[0] == "cr\r").toString())
                when (msgParts[0]) {
                    "mp" -> {
                        if (msgParts.size == 3) {
                            val motorNumber = Integer.parseInt(msgParts[1])
                            val position = msgParts[2].trim()
                            when (motorNumber) {
                                1 -> motor1_pos_tv.text = position
                                2 -> motor2_pos_tv.text = position
                                3 -> motor3_pos_tv.text = position
                                4 -> motor4_pos_tv.text = position
                            }

                            motorPos[motorNumber - 1] = position;
                        }
                    }
                    "pr" -> {
                        if (msgParts.size == 3) {
                            val motorNumber = Integer.parseInt(msgParts[1])
                            val speed = "${msgParts[2].trim()} s/sec"
                            when (motorNumber) {
                                1 -> motor1_speed_tv.text = speed
                                2 -> motor2_speed_tv.text = speed
                                3 -> motor3_speed_tv.text = speed
                                4 -> motor4_speed_tv.text = speed
                            }

                            motorSpeed[motorNumber - 1] = msgParts[2].trim();
                        }
                    }
                    "ac" -> {
                        if (msgParts.size == 3) {
                            val motorNumber = Integer.parseInt(msgParts[1])
                            val acc = "${msgParts[2].trim()} s/sec2"
                            when (motorNumber) {
                                1 -> motor1_acc_tv.text = acc
                                2 -> motor2_acc_tv.text = acc
                                3 -> motor3_acc_tv.text = acc
                                4 -> motor4_acc_tv.text = acc
                            }

                            motorAcc[motorNumber - 1] = msgParts[2].trim();
                        }
                    }
                    "cm" -> {
                        if (msgParts.size >= 2) {
                            val modeNumber = Integer.parseInt(msgParts[1].removeSuffix("\r"))
                            when (modeNumber) {
                                0 -> modeStatus_tv.text = "No Custom Mode Active"
                                1 -> {
                                    if (msgParts.size == 6) {
                                        val status = if (msgParts[2] == "0") "Setup"
                                        else if (msgParts[2] == "1") "Running"
                                        else if (msgParts[2] == "2") "Done"
                                        else if (msgParts[2] == "14") "Error Move Time"
                                        else if (msgParts[2] == "15") "Error Position"
                                        else "Error ${msgParts[2]}"
                                        val statusExecution = if (msgParts[3] == "0") "Init Move"
                                        else "Executing"
                                        val imageCounter = msgParts[4]
                                        val images = msgParts[5]

                                        modeStatus_tv.text = if (msgParts[2] == "2") "Timelapse Mode $status" else "Timelapse Mode $status Step: $statusExecution Images: $imageCounter/$images"
                                    }
                                }
                                2 -> {
                                    if (msgParts.size == 10) {
                                        val status = if (msgParts[2] == "0") "Setup"
                                        else if (msgParts[2] == "1") "Running"
                                        else "Done"
                                        val statusExecution = if (msgParts[3] == "0") "Rest"
                                        else if (msgParts[3] == "1") "Rest"
                                        else if (msgParts[3] == "2") "Image"
                                        else if (msgParts[3] == "3") "Move"
                                        else if (msgParts[3] == "4") "Move"
                                        else "Move End"
                                        val rowCounter = msgParts[4]
                                        val rowImages = msgParts[5]
                                        val columnCounter = msgParts[6]
                                        val columnImages = msgParts[7]
                                        val exposureTime = msgParts[8]
                                        val restMoveTime = msgParts[9]

                                        modeStatus_tv.text = if (msgParts[2] == "2") "Panorma Mode $status" else "Panorma Mode $status Step: $statusExecution Row: $rowCounter/$rowImages Column: $columnCounter/$columnImages Exposure: $exposureTime RestTime: $restMoveTime"
                                    }
                                }
                            }
                        }
                    }
                    "cr" -> modeStatus_tv.text = "No Custom Mode Active"
                }
            }

        }
    }

    fun connectBluetoothDevice() {
        ConnectToDevice(this).execute();
    }

    private fun disconnectBluetoothDevice() {
        if (mBound) {
            mService.disconnect()
        }

        val intent = Intent(this, ConnectMenu::class.java)
        startActivity(intent)
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context

        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            progress = ProgressDialog.show(context, "Connecting...", "Please wait")
        }

        override fun doInBackground(vararg params: Void?): String? {
            try {
                Log.i("Connect Background", "Start")
                if (mBound) {
                    mService.connect(address!!)
                }
            } catch (err: IOException) {
                connectSuccess = false
                err.printStackTrace()
            }

            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            if (!connectSuccess) {
                Log.i("data", "Could not connect")
                var intent = Intent(context, ConnectMenu::class.java)
                context.startActivity(intent)
            } else {
                isConnected = true
            }

            progress.dismiss()
        }
    }
}
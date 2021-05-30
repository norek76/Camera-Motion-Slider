package com.example.motioncontroller

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_custom_mode.*

enum class CustomModeType {
    JOG,
    TIMELAPSE,
    PANORAMA
}

const val CUSTOM_MODE_EXTRA = "CUSTOM_MODE_EXTRA"

class CustomMode : AppCompatActivity() {
    lateinit var mService: MotionControllerService

    var mBound: Boolean = false
    private var titleMessage = ""
    private lateinit var customMode: CustomModeType

    private var timelapse_start_m1: Int = 0
    private var timelapse_start_m2: Int = 0
    private var timelapse_start_m3: Int = 0
    private var timelapse_start_m4: Int = 0
    private var timelapse_end_m1: Int = 0
    private var timelapse_end_m2: Int = 0
    private var timelapse_end_m3: Int = 0
    private var timelapse_end_m4: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_mode)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val customModeExtra = intent?.getSerializableExtra(CUSTOM_MODE_EXTRA)
        if (customModeExtra != null) {
            customMode = customModeExtra as CustomModeType
            supportActionBar?.setTitle(titleMessage)
        }
    }

    fun initJogModeOnCreate() {
        titleMessage = getString(R.string.jog_mode)
        cl_jog.visibility = View.VISIBLE
        sb_jog_m1.setOnSeekBarChangeListener(jogModeSeekBar(1))
        sb_jog_m2.setOnSeekBarChangeListener(jogModeSeekBar(2))
        sb_jog_m3.setOnSeekBarChangeListener(jogModeSeekBar(3))
        sb_jog_m4.setOnSeekBarChangeListener(jogModeSeekBar(4))

        ib_jog_zero_m1.setOnLongClickListener(jogModeResetMotor(1))
        ib_jog_zero_m2.setOnLongClickListener(jogModeResetMotor(2))
        ib_jog_zero_m3.setOnLongClickListener(jogModeResetMotor(3))
        ib_jog_zero_m4.setOnLongClickListener(jogModeResetMotor(4))

        bt_jog_motorAllStop.setOnClickListener {
            if (mBound) {
                mService.stopAllMotor()
            }
        }

        if (mBound) {
            val motorCount = mService.motorCount

            if (motorCount >= 2) {
                tv_jog_m2.visibility = View.VISIBLE
                tv_jog_name_m2.visibility = View.VISIBLE
                tv_jog_position_m2.visibility = View.VISIBLE
                ib_jog_zero_m2.visibility = View.VISIBLE
                sb_jog_m2.visibility = View.VISIBLE
                tv_jog_name_m2.text = mService.getMotorName(2)
            }
            if (motorCount >= 3) {
                tv_jog_m3.visibility = View.VISIBLE
                tv_jog_name_m3.visibility = View.VISIBLE
                tv_jog_position_m3.visibility = View.VISIBLE
                ib_jog_zero_m3.visibility = View.VISIBLE
                sb_jog_m3.visibility = View.VISIBLE
                tv_jog_name_m3.text = mService.getMotorName(3)
            }
            if (motorCount >= 4) {
                tv_jog_m4.visibility = View.VISIBLE
                tv_jog_name_m4.visibility = View.VISIBLE
                tv_jog_position_m4.visibility = View.VISIBLE
                ib_jog_zero_m4.visibility = View.VISIBLE
                sb_jog_m4.visibility = View.VISIBLE
                tv_jog_name_m4.text = mService.getMotorName(4)
            }
        }
    }

    fun initTimelapseMode() {
        titleMessage = getString(R.string.timelapse_mode)
        cl_timelapse.visibility = View.VISIBLE

        sb_timelapse_m1.setOnSeekBarChangeListener(jogModeSeekBar(1))
        sb_timelapse_m2.setOnSeekBarChangeListener(jogModeSeekBar(2))
        sb_timelapse_m3.setOnSeekBarChangeListener(jogModeSeekBar(3))
        sb_timelapse_m4.setOnSeekBarChangeListener(jogModeSeekBar(4))

        tv_timelapse_start.setOnLongClickListener(timelapseStartPosition)
        tv_timelapse_end.setOnLongClickListener(timelapseEndPosition)
        tv_timelapse_start_m1.setOnLongClickListener {
            setTimelapsePosition(1, tv_timelapse_start_m1)
            true
        }
        tv_timelapse_start_m2.setOnLongClickListener {
            setTimelapsePosition(2, tv_timelapse_start_m2)
            true
        }
        tv_timelapse_start_m3.setOnLongClickListener {
            setTimelapsePosition(3, tv_timelapse_start_m3)
            true
        }
        tv_timelapse_start_m4.setOnLongClickListener {
            setTimelapsePosition(4, tv_timelapse_start_m4)
            true
        }
        tv_timelapse_end_m1.setOnLongClickListener {
            setTimelapsePosition(1, tv_timelapse_end_m1)
            true
        }
        tv_timelapse_end_m2.setOnLongClickListener {
            setTimelapsePosition(2, tv_timelapse_end_m2)
            true
        }
        tv_timelapse_end_m3.setOnLongClickListener {
            setTimelapsePosition(3, tv_timelapse_end_m3)
            true
        }
        tv_timelapse_end_m4.setOnLongClickListener {
            setTimelapsePosition(4, tv_timelapse_end_m4)
            true
        }

        bt_timelapse_reset.setOnLongClickListener {
            if (mBound) {
                mService.resetCustomMode()
            }
            true
        }

        bt_timelapse_stop_motor.setOnClickListener {
            if (mBound) {
                mService.stopAllMotor()
            }
        }

        iv_timelapse_start_goto.setOnLongClickListener {
            if (mBound) {
                mService.goToPosition(1,timelapse_start_m1)
                mService.goToPosition(2,timelapse_start_m2)
                mService.goToPosition(3,timelapse_start_m3)
                mService.goToPosition(4,timelapse_start_m4)
            }

            true
        }

        iv_timelapse_end_goto.setOnLongClickListener {
            if (mBound) {
                mService.goToPosition(1,timelapse_end_m1)
                mService.goToPosition(2,timelapse_end_m2)
                mService.goToPosition(3,timelapse_end_m3)
                mService.goToPosition(4,timelapse_end_m4)
            }

            true
        }

        if (mBound) {
            val motorCount = mService.motorCount

            if (motorCount >= 2) {
                tv_timelapse_m2.visibility = View.VISIBLE
                tv_timelapse_position_m2.visibility = View.VISIBLE
                tv_timelapse_start_m2.visibility = View.VISIBLE
                tv_timelapse_end_m2.visibility = View.VISIBLE
                sb_timelapse_m2.visibility = View.VISIBLE
            }
            if (motorCount >= 3) {
                tv_timelapse_m3.visibility = View.VISIBLE
                tv_timelapse_position_m3.visibility = View.VISIBLE
                tv_timelapse_start_m3.visibility = View.VISIBLE
                tv_timelapse_end_m3.visibility = View.VISIBLE
                sb_timelapse_m3.visibility = View.VISIBLE
            }
            if (motorCount >= 4) {
                tv_timelapse_m4.visibility = View.VISIBLE
                tv_timelapse_position_m4.visibility = View.VISIBLE
                tv_timelapse_start_m4.visibility = View.VISIBLE
                tv_timelapse_end_m4.visibility = View.VISIBLE
                sb_timelapse_m4.visibility = View.VISIBLE
            }
        }
    }

    val timelapseStartPosition : View.OnLongClickListener = object : View.OnLongClickListener {
        override fun onLongClick(v: View?): Boolean {
            if (mBound) {
                timelapse_start_m1 = setTimelapsePosition(1, tv_timelapse_start_m1)

                if (mService.motorCount >= 2) {
                    timelapse_start_m2 = setTimelapsePosition(2, tv_timelapse_start_m2)
                }
                if (mService.motorCount >= 3) {
                    timelapse_start_m3 = setTimelapsePosition(3, tv_timelapse_start_m3)
                }
                if (mService.motorCount >= 4) {
                    timelapse_start_m4 = setTimelapsePosition(4, tv_timelapse_start_m4)
                }
            }

            return true
        }
    }

    val timelapseEndPosition: View.OnLongClickListener = object : View.OnLongClickListener {
        override fun onLongClick(v: View?): Boolean {
            if (mBound) {
                timelapse_end_m1 = setTimelapsePosition(1, tv_timelapse_end_m1)

                if (mService.motorCount >= 2) {
                    timelapse_end_m2 = setTimelapsePosition(2, tv_timelapse_end_m2)
                }
                if (mService.motorCount >= 3) {
                    timelapse_end_m3 = setTimelapsePosition(3, tv_timelapse_end_m3)
                }
                if (mService.motorCount >= 4) {
                    timelapse_end_m4 = setTimelapsePosition(4, tv_timelapse_end_m4)
                }
            }

            return true
        }
    }

    fun setTimelapsePosition(motorNumber: Int, tv_timelapse_start: TextView): Int {
        var position = 0
        if (mBound) {
            position = mService.getMotorPosition(motorNumber)
            val realPosition = mService.getMotorRealPosition(motorNumber, position)
            tv_timelapse_start.text = realPosition ?: position.toString()
        }

        return position
    }

    fun initPanoramaMode() {
        titleMessage = getString(R.string.panorama_mode)
        cl_panorama.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        connectMotionControlService()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageControllerUpdate, IntentFilter(MotionControllerService.EXTRA_MOTION_CONTROLLER_UPDATE))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageCustomModeUpdate, IntentFilter(MotionControllerService.EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messagePositionUpdate, IntentFilter(MotionControllerService.MOTION_CONTROLLER_POSITION_UPDATE_ACTION))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageDisconnect, IntentFilter(MotionControllerService.MOTION_CONTROLLER_DISCONNECT_ACTION))

        validateConnection()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageControllerUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageCustomModeUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messagePositionUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageDisconnect)
        super.onPause()
    }

    private fun connectMotionControlService() {
        Intent(this, MotionControllerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MotionControllerService.MotionControllerBinder
            mService = binder.getService()
            mBound = true
            mService.updateNotification(
                updateNotificationText = titleMessage,
                updateNotificationActivity = CustomMode::class.java as Class<AppCompatActivity>,
                extraCustomMode = customMode
            )

            when(customMode) {
                CustomModeType.JOG -> {
                    initJogModeOnCreate()
                }
                CustomModeType.TIMELAPSE -> {
                    initTimelapseMode()
                }
                CustomModeType.PANORAMA -> {
                    initPanoramaMode()
                }
            }

            validateConnection()
            mService.requestInitData()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("Binding Stopped", "Binding")
            mBound = false
        }
    }

    fun validateConnection() {
        if (!mBound) {
            connectMotionControlService()
        } else if (!mService.isConnected) {
            returnConnectMenu()
        }
    }

    private fun returnConnectMenu() {
        val intent = Intent(this, ConnectMenu::class.java)
        startActivity(intent)
    }

    private fun returnMainPage() {
        val intent = Intent(this, MainPage::class.java)
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                returnMainPage()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true
    }

    fun jogModeResetMotor(motorNumber: Int) : View.OnLongClickListener {
        return object : View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {

                if (mBound) {
                    mService.resetMotorPosition(motorNumber)
                }
                return true
            }
        }
    }

    fun jogModeSeekBar(motorNumber: Int) : SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                Log.i("SeekBar", seekBar.progress.toString())
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                Log.i("SeekBarTouch", seekBar.progress.toString())
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seekBar.progress = 50;
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }
        }
    }

    private val messageControllerUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

        }
    }

    private val messageCustomModeUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

        }
    }

    private val messagePositionUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val motorNumber = intent.getStringExtra(MotionControllerService.MOTION_CONTROLLER_POSITION_UPDATE_EXTRA)?.toIntOrNull()
            if (motorNumber != null && mBound) {
                val position = mService.getMotorPosition(motorNumber)
                var text = "${getString(R.string.motor_position)} $position"

                val motorRealPosition = mService.getMotorRealPosition(motorNumber, position)
                if (motorRealPosition != null) {
                    text += " / $motorRealPosition"
                }

                when (customMode) {
                    CustomModeType.JOG -> updateJogPosition(motorNumber, text)
                    CustomModeType.TIMELAPSE -> updateTimelapsePosition(motorNumber, text)
                }
            }
        }
    }

    private fun updateJogPosition(motorNumber: Int, text: String) {
        when (motorNumber) {
            1 -> {
                tv_jog_position_m1.text = text
            }
            2 -> {
                tv_jog_position_m2.text = text
            }
            3 -> {
                tv_jog_position_m3.text = text
            }
            4 -> {
                tv_jog_position_m4.text = text
            }
        }
    }

    private fun updateTimelapsePosition(motorNumber: Int, text: String) {
        when (motorNumber) {
            1 -> {
                tv_timelapse_position_m1.text = text
            }
            2 -> {
                tv_timelapse_position_m2.text = text
            }
            3 -> {
                tv_timelapse_position_m3.text = text
            }
            4 -> {
                tv_timelapse_position_m4.text = text
            }
        }
    }

//    private fun timelapse() {
//        val timelapseData = object : TimelapseData {
//            override val images: Int = 100
//            override val interval: Int = 5000
//            override val exposureTime: Int = 1000
//            override val restTime: Int = 500
//            override val motorData: Array<TimelapseDataMotor> = arrayOf(
//                object : TimelapseDataMotor {
//                    override val motorNumber: Int = 1
//                    override val positions: IntArray = intArrayOf(100, 200)
//                },
//                object : TimelapseDataMotor {
//                    override val motorNumber: Int = 2
//                    override val positions: IntArray = intArrayOf(100, 200)
//                }
//            )
//        }
//        mService.startTimelapse(timelapseData)
//    }

    private val messageDisconnect: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            disconnectBluetoothDevice()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun disconnectBluetoothDevice() {
        val prefEditor = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.edit()
        prefEditor?.remove("cnt_address")
        prefEditor?.commit()

        if (mBound) {
            mService.disconnect()
        }

        MotionControllerService.stopService(this)
        returnConnectMenu()
    }
}
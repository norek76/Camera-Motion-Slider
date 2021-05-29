package com.example.motioncontroller

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.motioncontroller.datasets.TimelapseData
import com.example.motioncontroller.datasets.TimelapseDataMotor
import kotlinx.android.synthetic.main.activity_custom_mode.*

enum class CUSTOM_MODE_TYPE {
    JOG,
    TIMELAPSE,
    PANORAMA
}

const val CUSTOM_MODE_EXTRA = "CUSTOM_MODE_EXTRA"

class CustomMode : AppCompatActivity() {
    lateinit var mService: MotionControllerService

    var mBound: Boolean = false
    private var titleMessage = ""
    private lateinit var customMode: CUSTOM_MODE_TYPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_mode)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val customModeExtra = intent?.getSerializableExtra(CUSTOM_MODE_EXTRA)
        if (customModeExtra != null) {
            customMode = customModeExtra as CUSTOM_MODE_TYPE

            when(customMode) {
                CUSTOM_MODE_TYPE.JOG -> {
                    initJogMode()
                }
                CUSTOM_MODE_TYPE.TIMELAPSE -> {
                    initTimelapseMode()
                }
                CUSTOM_MODE_TYPE.PANORAMA -> {
                    initPanoramaMode()
                }
            }

            supportActionBar?.setTitle(titleMessage)
        }
    }

    fun initJogMode() {
        titleMessage = getString(R.string.jog_mode)
        cl_jog.visibility = View.VISIBLE
        sb_motor_1.setOnSeekBarChangeListener(jogModeSeekBar(1))
        sb_motor_2.setOnSeekBarChangeListener(jogModeSeekBar(2))
        sb_motor_3.setOnSeekBarChangeListener(jogModeSeekBar(3))
        sb_motor_4.setOnSeekBarChangeListener(jogModeSeekBar(4))

        ib_zero_1.setOnLongClickListener(jogModeResetMotor(1))
        ib_zero_2.setOnLongClickListener(jogModeResetMotor(2))
        ib_zero_3.setOnLongClickListener(jogModeResetMotor(3))
        ib_zero_4.setOnLongClickListener(jogModeResetMotor(4))

        bt_motorAllStop.setOnClickListener {
            if (mBound) {
                mService.stopAllMotor()
            }
        }
    }

    fun initTimelapseMode() {
        titleMessage = getString(R.string.timelapse_mode)
        cl_timelapse.visibility = View.VISIBLE
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
            .registerReceiver(messageCustomerModeUpdate, IntentFilter(MotionControllerService.EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messagePositionUpdate, IntentFilter(MotionControllerService.MOTION_CONTROLLER_POSITION_UPDATE_ACTION))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageDisconnect, IntentFilter(MotionControllerService.MOTION_CONTROLLER_DISCONNECT_ACTION))

        validateConnection()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageControllerUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageCustomerModeUpdate)
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

            tv_motor_1_name.text = mService.getMotorName(1)
            tv_motor_2_name.text = mService.getMotorName(2)
            tv_motor_3_name.text = mService.getMotorName(3)
            tv_motor_4_name.text = mService.getMotorName(4)

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
                // Display the current progress of SeekBar
                Log.i("SeekBar", seekBar.progress.toString())
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Do something
                Log.i("SeekBarTouch", seekBar.progress.toString())
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Do something
                seekBar.progress = 50;
                mService.jogModeEvent(motorNumber, seekBar.progress - 50)
            }
        }
    }

    private val messageControllerUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

        }
    }

    private val messageCustomerModeUpdate: BroadcastReceiver = object : BroadcastReceiver() {
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
                when (motorNumber) {
                    1 -> {
                        tv_position_1.text = text
                    }
                    2 -> {
                        tv_position_2.text = text
                    }
                    3 -> {
                        tv_position_3.text = text
                    }
                    4 -> {
                        tv_position_4.text = text
                    }
                }
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
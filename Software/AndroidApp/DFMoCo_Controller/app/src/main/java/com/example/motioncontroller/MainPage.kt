package com.example.motioncontroller

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.motioncontroller.MotionControllerService.Companion.EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE
import com.example.motioncontroller.MotionControllerService.Companion.MOTION_CONTROLLER_DISCONNECT_ACTION
import com.example.motioncontroller.MotionControllerService.Companion.EXTRA_MOTION_CONTROLLER_UPDATE
import com.example.motioncontroller.datasets.TimelapseData
import com.example.motioncontroller.datasets.TimelapseDataMotor
import kotlinx.android.synthetic.main.activity_main_page.*

@Suppress("UNCHECKED_CAST")
class MainPage : AppCompatActivity() {
    lateinit var mService: MotionControllerService

    private var mBound: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_page)

        ib_jog.setOnClickListener {
            goToModeActivity(CUSTOM_MODE_TYPE.JOG)
        }
        ib_reset.setOnLongClickListener {
            if (mBound) {
                mService.resetCustomMode()
            }
            true
        }
        ib_timelapse.setOnClickListener {
            goToModeActivity(CUSTOM_MODE_TYPE.TIMELAPSE)
        }
        ib_panorama.setOnClickListener {
            goToModeActivity(CUSTOM_MODE_TYPE.PANORAMA)
        }
    }

    override fun onStart() {
        super.onStart()
        connectMotionControlService()
    }

    private fun connectMotionControlService() {
        Intent(this, MotionControllerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_ABOVE_CLIENT)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_page_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.disconnect_bt -> disconnectBluetoothDevice()
        }

        return true
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageControllerUpdate, IntentFilter(EXTRA_MOTION_CONTROLLER_UPDATE))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageCustomerModeUpdate, IntentFilter(EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageDisconnect, IntentFilter(MOTION_CONTROLLER_DISCONNECT_ACTION))

        validateConnection()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageControllerUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageCustomerModeUpdate)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageDisconnect)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MotionControllerService.MotionControllerBinder
            mService = binder.getService()
            mBound = true
            mService.updateNotification(
                updateNotificationText = "Connected Main Page",
                updateNotificationActivity = MainPage::class.java as Class<AppCompatActivity>,
                extraCustomMode = null
            )
            validateConnection()
            mService.requestInitData()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("Binding Stopped", "Binding")
            mBound = false
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            Log.i("Binding Died", "Binding")
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            Log.i("Binding Null", "Binding")
        }
    }

    fun validateConnection() {
        if (!mBound) {
            connectMotionControlService()
        } else if (!mService.isConnected) {
            returnConnectMenu()
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

    private fun goToModeActivity(customMode: CUSTOM_MODE_TYPE) {
        val intent = Intent(this, CustomMode::class.java)
        intent.putExtra(CUSTOM_MODE_EXTRA, customMode)

        startActivity(intent)
    }

    private fun returnConnectMenu() {
        val intent = Intent(this, ConnectMenu::class.java)
        startActivity(intent)
    }

    fun getMotorPosition(motorNumber: Int): String {
        return if (mBound) mService.getMotorPosition(motorNumber).toString() else "-1"
    }

    private fun getMotorSpeed(motorNumber: Int): Int {
        return if (mBound) mService.getMotorSpeed(motorNumber) else -1
    }

    private fun getMotorAcc(motorNumber: Int): Int {
        return if (mBound) mService.getMotorAcc(motorNumber) else -1
    }

    fun sendCommand(cmd: String) {
        if (mBound) {
            mService.sendCommand(cmd)
        }
    }

    private val messageControllerUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            version.text = "${getString(R.string.version)} ${mService.version}"
            motorCount.text = "${getString(R.string.motor_count)} ${mService.motorCount}"
        }
    }

    private val messageCustomerModeUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mBound) {
                when(mService.customMode) {
                    MotionControllerCustomMode.NO_CUSTOM_MODE -> {
                        ib_timelapse.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_panorama.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_focus.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                    }
                    MotionControllerCustomMode.TIMELAPSE -> {
                        ib_timelapse.setColorFilter(ResourcesCompat.getColor(resources, R.color.orange, null))
                        ib_panorama.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_focus.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                    }
                    MotionControllerCustomMode.PANORAMA -> {
                        ib_timelapse.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_panorama.setColorFilter(ResourcesCompat.getColor(resources, R.color.orange, null))
                        ib_focus.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                    }
                    MotionControllerCustomMode.FOCUS_STACKING -> {
                        ib_timelapse.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_panorama.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null))
                        ib_focus.setColorFilter(ResourcesCompat.getColor(resources, R.color.orange, null))
                    }
                }
            }
        }
    }

    private val messageDisconnect: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            disconnectBluetoothDevice()
        }
    }
}
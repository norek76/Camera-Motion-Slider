package com.example.motioncontroller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.motioncontroller.fragments.Jog
import com.example.motioncontroller.fragments.Panorama
import com.example.motioncontroller.fragments.TimelapseFragment

enum class CUSTOM_MODE_TYPE {
    JOG,
    TIMELAPSE,
    PANORAMA
}

const val CUSTOM_MODE_EXTRA = "CUSTOM_MODE_EXTRA"

class CustomMode : AppCompatActivity() {
    lateinit var mService: MotionControllerService

    private var mBound: Boolean = false
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
                    titleMessage = getString(R.string.jog_mode)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fl_mode, Jog())
                        .commit()
                }
                CUSTOM_MODE_TYPE.TIMELAPSE -> {
                    titleMessage = getString(R.string.timelapse_mode)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fl_mode, TimelapseFragment())
                        .commit()
                }
                CUSTOM_MODE_TYPE.PANORAMA -> {
                    titleMessage = getString(R.string.panorama_mode)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fl_mode, Panorama())
                        .commit()
                }
            }

            supportActionBar?.setTitle(titleMessage)
        }
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
}
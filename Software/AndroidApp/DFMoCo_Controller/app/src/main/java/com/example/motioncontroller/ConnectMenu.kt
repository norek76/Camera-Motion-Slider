@file:Suppress("DEPRECATION")

package com.example.motioncontroller

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.AsyncTask
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_connect_menu.*
import java.io.IOException

private const val REQUEST_CODE_ENABLE_BT:Int = 1

class ConnectMenu : AppCompatActivity() {
    lateinit var bAdapter: BluetoothAdapter
    private lateinit var pairedDevices: Set<BluetoothDevice>

    private var btAddress: String? = null

    companion object {
        private var mBound: Boolean = false
        lateinit var progressBar: ProgressBar
        lateinit var deviceListView: ListView
        lateinit var infoTextView: TextView
        lateinit var mService: MotionControllerService
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_menu)

        bAdapter = BluetoothAdapter.getDefaultAdapter()
        progressBar = pb_connecting
        deviceListView = lv_pairedDevices
        infoTextView = tv_infoConnection
        bluetoothAdapterIcon()
    }

    private fun bluetoothAdapterIcon() {
        if (bAdapter.isEnabled) {
            bluetoothIv.setImageResource(R.drawable.ic_bluetooth_on)
        } else {
            bluetoothIv.setImageResource(R.drawable.ic_bluetooth_off)
        }
    }

    override fun onStart() {
        super.onStart()

        checkIfBluetoothIfActivated()
    }

    private fun checkIfBluetoothIfActivated() {
        if (!bAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_CODE_ENABLE_BT)
        } else {
            val sharedPref =
                getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)
            btAddress = sharedPref.getString("cnt_address", null)
            if (btAddress !== null) {
                connectMotionControlService()
            } else {
                pairedDeviceList()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_ENABLE_BT ->
                if (resultCode == Activity.RESULT_OK) {
                    bluetoothIv.setImageResource(R.drawable.ic_bluetooth_on)
                    Toast.makeText(this, "Bluetooth is on", Toast.LENGTH_LONG).show()
                    pairedDeviceList()
                } else {
                    Toast.makeText(this, "Bluetooth could not be turned on", Toast.LENGTH_LONG).show()
                }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun connectMotionControlService() {
        MotionControllerService.startService(this)
        Intent(this, MotionControllerService::class.java).also { intent ->
            bindService(intent, connection(::validateConnection), Context.BIND_ABOVE_CLIENT)
        }
    }

    private fun stopMotionControlService() {
        MotionControllerService.stopService(this)
    }

    fun connection(callbackFunction: (connectSuccess: Boolean) -> Unit) = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i("Service", "Conne")
            val binder = service as MotionControllerService.MotionControllerBinder
            mService = binder.getService()
            mBound = true

            if (btAddress !== null) {
                ConnectToDevice(this@ConnectMenu, btAddress!!, callbackFunction).execute()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private class ConnectToDevice(c: Context, val address: String, callbackFunction: (connectSuccess: Boolean) -> Unit) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = false
        @SuppressLint("StaticFieldLeak")
        private val callbackFunction: (connectSuccess: Boolean) -> Unit = callbackFunction

        override fun onPreExecute() {
            super.onPreExecute()
            progressBar.visibility = View.VISIBLE
            deviceListView.visibility = View.INVISIBLE
            infoTextView.setText(R.string.connecting)
        }

        override fun doInBackground(vararg params: Void?): String? {
            try {
                if (mBound) {
                    mService.connectAndInit(address)
                    if (mService.isConnected) {
                        connectSuccess = true
                    }
                }
            } catch (err: IOException) {
                err.printStackTrace()
            }

            return null
        }

        @SuppressLint("ApplySharedPref")
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            callbackFunction(connectSuccess)
        }
    }

    private fun validateConnection(connectSuccess: Boolean) {
        progressBar.visibility = View.GONE

        if (!connectSuccess) {
            val prefEditor = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.edit()
            prefEditor?.remove("cnt_address")
            prefEditor?.commit()

            stopMotionControlService()

            tv_infoConnection.setText(R.string.device_selection)
            deviceListView.visibility = View.VISIBLE

            checkIfBluetoothIfActivated()
        } else {
            intent = Intent(this, MainPage::class.java)
            startActivity(intent)
        }
    }

    private fun pairedDeviceList() {
        pairedDevices = bAdapter.bondedDevices
        val list : ArrayList<BluetoothDevice> = ArrayList()
        val listNames : ArrayList<String> = ArrayList()

        if (pairedDevices.isNotEmpty()) {
            for (device: BluetoothDevice in pairedDevices) {
                if (device.name.contains("DFMoCo")) {
                    list.add(device)
                    listNames.add(device.name)
                    Log.i("device", "" + device)
                }
            }
        } else {
            Toast.makeText(this, "No paired device available...", Toast.LENGTH_LONG).show()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listNames)
        lv_pairedDevices.adapter = adapter
        lv_pairedDevices.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val device = list[position]
            val address = device.address

            val prefEditor = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)?.edit()
            prefEditor?.putString("cnt_address", address)
            prefEditor?.apply()

            btAddress = address
            connectMotionControlService()
        }
    }
}
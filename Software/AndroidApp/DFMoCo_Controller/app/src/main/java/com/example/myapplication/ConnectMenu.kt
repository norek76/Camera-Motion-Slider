package com.example.myapplication

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.android.synthetic.main.activity_connect_menu.*


class ConnectMenu : AppCompatActivity() {

    private val REQUEST_CODE_ENABLE_BT:Int = 1;

    lateinit var bAdapter: BluetoothAdapter
    lateinit var pairedDevices: Set<BluetoothDevice>

    companion object {
        val EXTRA_ADDRESS: String = "Device_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_menu)

        bAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bAdapter == null) {
            bluetoothStatusTv.text = "Bluetooth is not available!"
        } else {
            bluetoothStatusTv.text = "Bluetooth is available!"
        }

        if (bAdapter.isEnabled) {
            bluetoothIv.setImageResource(R.drawable.ic_bluetooth_on)
        } else {
            bluetoothIv.setImageResource(R.drawable.ic_bluetooth_off)
        }

        if (!bAdapter.isEnabled) {
            var intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_CODE_ENABLE_BT);
        } else {
            pairedDeviceList()
        }
    }

    fun pairedDeviceList() {
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
        pairedLv.adapter = adapter
        pairedLv.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val device = list[position]
            val address = device.address

            val intent = Intent(this, MainMenu::class.java)
            intent.putExtra(EXTRA_ADDRESS, address)

            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_ENABLE_BT ->
                if (resultCode == Activity.RESULT_OK) {
                    bluetoothIv.setImageResource(R.drawable.ic_bluetooth_on)
                    Toast.makeText(this, "Bluetooth is on", Toast.LENGTH_LONG).show()
                    pairedDeviceList();
                } else {
                    Toast.makeText(this, "Bluetooth could not be turned on", Toast.LENGTH_LONG).show()
                }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
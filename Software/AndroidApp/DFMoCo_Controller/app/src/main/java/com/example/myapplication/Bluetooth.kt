package com.example.myapplication

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main_menu.*
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class BluetoothService: Service() {
    var myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    var bluetoothSocket: BluetoothSocket? = null
    var bluetoothInput: InputStream? = null
    lateinit var bluetoothAdapter: BluetoothAdapter
    var isConnected: Boolean = false
    private val binder = BluetoothBinder()

    companion object {
        val EXTRA_BLUETOOTH_DATA: String = "bluetooth-data"
        val EXTRA_BLUETOOTH_MESSAGE: String = "bluetooth-message"
    }


    inner class BluetoothBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    fun sendCommand(input: String) {
        Log.i("DATA SEND: ", input)
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket!!.outputStream.write("$input\r\n".toByteArray())
            } catch (err: IOException) {
                err.printStackTrace()
            }
        }
    }

    private fun sendMessageToActivity(msg: String) {
        val intent = Intent(EXTRA_BLUETOOTH_DATA)
        intent.putExtra(EXTRA_BLUETOOTH_MESSAGE, msg)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun connect(address: String?) {
        if ((bluetoothSocket == null || !isConnected) && address != null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(address)
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID)
            bluetoothAdapter.cancelDiscovery()
            bluetoothInput = bluetoothSocket!!.inputStream
            bluetoothSocket!!.connect()
            isConnected = true
            requestInitData()
            listen()
        }
    }

    fun disconnect() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket!!.close()
                bluetoothSocket = null
                isConnected = false
            } catch (err: IOException) {
                err.printStackTrace()
            }
        }
    }

    private fun listen() {
        var readBufferPosition = 0
        var readBuffer = ByteArray(1024)
        var msg = ""
        thread {
            while (isConnected && bluetoothInput != null) {
                val bytesAvailable = bluetoothInput!!.available()
                if (bytesAvailable > 0) {
                    var data = ByteArray(bytesAvailable)
                    bluetoothInput!!.read(data, 0, bytesAvailable)

                    for (b in data) {
                        if (b == 10.toByte()) {
                            val encodedBytes = ByteArray(readBufferPosition)
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)
                            msg = String(encodedBytes, charset("US-ASCII"))
                            readBufferPosition = 0
                            sendMessageToActivity(msg)
                        } else {
                            readBuffer.set(readBufferPosition++, b)
                        }
                    }
                }

                Thread.sleep(100)
            }
        }
    }

    fun requestInitData() {
        sendCommand("hi")
        sendCommand("mp 1")
        sendCommand("mp 2")
        sendCommand("mp 3")
        sendCommand("mp 4")
        sendCommand("ve 1")
        sendCommand("ve 2")
        sendCommand("ve 3")
        sendCommand("ve 4")
        sendCommand("ac 1")
        sendCommand("ac 2")
        sendCommand("ac 3")
        sendCommand("ac 4")
        sendCommand("cm")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}
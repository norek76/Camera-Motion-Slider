package com.example.motioncontroller

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread


open class BluetoothService: Service() {
    private val myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothInput: InputStream? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    var isConnected: Boolean = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    protected fun connect(address: String?) {
        Log.d("BluetoothService", "Connecting")
        if ((bluetoothSocket == null || !isConnected) && address != null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID)
            bluetoothAdapter!!.cancelDiscovery()
            bluetoothInput = bluetoothSocket!!.inputStream
            bluetoothSocket!!.connect()
            isConnected = true
            Log.d("BluetoothService", "Connected")
        }
    }

    fun disconnect() {
        Log.d("BluetoothService", "Disconnect")
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

    protected fun sendBluetoothCommand(input: String) {
        Log.d("BluetoothService", "Send $input")
        if (bluetoothSocket != null && isConnected) {
            bluetoothSocket!!.outputStream.write("$input\r\n".toByteArray())
        } else {
            throw Error("No bluetooth socket available.")
        }
    }

    fun listen() {
        var readBufferPosition = 0
        val readBuffer = ByteArray(1024)
        var msg: String

        thread {
            while (isConnected && bluetoothInput != null) {
                val bytesAvailable = bluetoothInput!!.available()
                if (bytesAvailable > 0) {
                    val data = ByteArray(bytesAvailable)
                    bluetoothInput!!.read(data, 0, bytesAvailable)

                    for (b in data) {
                        if (b == 10.toByte()) {
                            val encodedBytes = ByteArray(readBufferPosition)
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)
                            msg = String(encodedBytes, charset("US-ASCII"))
                            readBufferPosition = 0
                            messageReceived(msg)
                        } else {
                            readBuffer[readBufferPosition++] = b
                        }
                    }
                }

                Thread.sleep(100)
            }
        }
    }

    protected open fun messageReceived(msg: String?) {
        if (msg != null) {
            Log.d("BluetoothService", "Received: $msg")
        }
    }
}
package com.example.motioncontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.motioncontroller.datasets.MotorChannel
import com.example.motioncontroller.datasets.TimelapseData
import kotlinx.android.synthetic.main.activity_main_page.*

private const val CHANNEL_ID = "Motion_Controller_Service"
private const val NOTIFICATION_ID = 1

enum class MotionControllerCustomMode {
    NO_CUSTOM_MODE,
    TIMELAPSE,
    PANORAMA,
    FOCUS_STACKING,
}

@Suppress("UNCHECKED_CAST")
class MotionControllerService : BluetoothService() {
    private val binder = MotionControllerBinder()
    private val motors : List<MotorChannel> = List(8) { MotorChannel() }

    private var mHandler: Handler? = null
    private var jogModeState = 0
    private var jogModeMotor = 0

    private var notificationText = App.getContext().resources.getString(R.string.connecting)
    private var notificationActivity : Class<AppCompatActivity> = MainPage::class.java as Class<AppCompatActivity>
    private val notificationService = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(App.getContext().resources.getString(R.string.app_name))
        .setSmallIcon(R.drawable.ic_bluetooth_on)

    var motorCount = 0
    var version = ""
    var customMode = MotionControllerCustomMode.NO_CUSTOM_MODE

    companion object {
        const val EXTRA_MOTION_CONTROLLER_DATA: String = "bluetooth-data"
        const val EXTRA_MOTION_CONTROLLER_MESSAGE: String = "bluetooth-message"
        const val EXTRA_MOTION_CONTROLLER_UPDATE = "motion-controller-update"
        const val EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE = "motion-controller-custom-mode-update"
        const val EXTRA_MOTION_CONTROLLER_POSITION_UPDATE: String = "motion-controller-position-update"
        const val EXTRA_MOTION_CONTROLLER_SPEED_UPDATE: String = "motion-controller-speed-update"
        const val EXTRA_MOTION_CONTROLLER_ACC_UPDATE: String = "motion-controller-acc-update"
        const val EXTRA_MOTION_CONTROLLER_DISCONNECT: String = "motion-controller-disconnect"

        fun startService(context: Context) {
            val startIntent = Intent(context, MotionControllerService::class.java)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, MotionControllerService::class.java)
            context.stopService(stopIntent)
        }
    }

    inner class MotionControllerBinder : Binder() {
        fun getService(): MotionControllerService = this@MotionControllerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MotionControllerService", "Start")

        startForegroundService()

        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification(null)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        Log.d("MotionControllerService", "Stop foreground service.")

        // Stop foreground service and remove the notification.
        stopForeground(true)

        // Stop the foreground service.
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Motion_Controller_Service_Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            serviceChannel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(extraCustomMode: CUSTOM_MODE_TYPE?): Notification {
        val notificationIntent = Intent(this, notificationActivity)
        if (extraCustomMode != null) {
            notificationIntent.putExtra(CUSTOM_MODE_EXTRA, extraCustomMode)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        return notificationService
            .setContentText(notificationText)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateNotification(updateNotificationText: String?, updateNotificationActivity : Class<AppCompatActivity>?, extraCustomMode: CUSTOM_MODE_TYPE?) {
        if (updateNotificationText != null) {
            notificationText = updateNotificationText
        }
        if (updateNotificationActivity != null) {
            notificationActivity = updateNotificationActivity
        }

        val notification = createNotification(extraCustomMode)
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun connectAndInit(address: String) {
        connect(address)
        if (isConnected) {
            updateNotification(App.getContext().resources.getString(R.string.connected), null, null)
            requestInitData()
            listen()
        }
    }

    fun getMotorPosition(motorNumber: Int): Int {
        return motors[motorNumber - 1].position
    }

    fun getMotorSpeed(motorNumber: Int): Int {
        return motors[motorNumber - 1].speed
    }

    fun getMotorAcc(motorNumber: Int): Int {
        return motors[motorNumber - 1].acc
    }

    @Suppress("DEPRECATION")
    fun jogModeEvent(motionEvent: Int, activeMotor: Int, jog: Boolean, direction: Boolean) {
        when (motionEvent) {
            MotionEvent.ACTION_DOWN -> {
                if (jogModeState == 0 && activeMotor > 0) {
                    jogModeState = 2
                    jogModeMotor = activeMotor
                    if (mHandler != null) {
                        mHandler = null
                    }
                    mHandler = Handler()
                    jogModeMovement(jog, direction)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (jogModeState == 2) {
                    sendCommand("sm $jogModeMotor")
                    jogModeState = 1
                    Handler().postDelayed({
                        jogModeMotor = 0
                        jogModeState = 0
                    }, 50)
                }

                mHandler = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (jogModeState == 2) {
                    sendCommand("sm $jogModeMotor")
                    jogModeState = 1
                    Handler().postDelayed({
                        jogModeMotor = 0
                        jogModeState = 0
                    }, 50)
                }

                mHandler = null
            }
        }
    }

    private fun jogModeMovement(jog: Boolean, direction: Boolean) {
        val cmd = if (jog) "jm" else "im"
        val position = if (direction) "500000" else "-500000"

        if (jogModeState == 2) {
            sendCommand("$cmd $jogModeMotor $position")
        }

        if (mHandler != null) {
            mHandler!!.postDelayed({ jogModeMovement(jog, direction) }, 300)
        }
    }

    fun requestInitData() {
        val motorNumbers = (1 until (motorCount + 1) step 1)

        requestVersion()
        for (motorNumber in motorNumbers) {
            requestPostion(motorNumber)
            requestSpeed(motorNumber)
            requestAcc(motorNumber)
        }
        requestCustomMode()
    }

    fun requestVersion() {
        sendCommand("hi")
    }

    fun requestPostion(motorNumber: Int) {
        sendCommand("mp $motorNumber")
    }

    fun requestSpeed(motorNumber: Int) {
        sendCommand("ve $motorNumber")
    }

    fun requestAcc(motorNumber: Int) {
        sendCommand("ac $motorNumber")
    }

    fun requestCustomMode() {
        sendCommand("cm")
    }

    fun resetCustomMode() {
        sendCommand("cr")
    }

    fun resetMotorPosition(motorNumber: Int) {
        sendCommand("zm $motorNumber")
    }

    fun startTimelapse(timelapseData: TimelapseData) {
        var command = ""
        command += "tl ${timelapseData.images} ${timelapseData.interval} ${timelapseData.exposureTime} ${timelapseData.restTime}"

        timelapseData.motorData.forEach { motorDataEntry ->
            command += " ${motorDataEntry.motorNumber} ${motorDataEntry.positions[0]} ${motorDataEntry.positions[1]}"
        }

        sendCommand(command)
    }

    override fun messageReceived(msg: String?) {
        if (msg != null && msg != "") {
            Log.i("data", msg)
            val msgParts = msg.split(" ")
            when (msgParts[0]) {
                "hi" -> {
                    if (msgParts.size == 4) {
                        motorCount = Integer.parseInt(msgParts[2].trim())
                        version = msgParts[3].trim()
                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_UPDATE, null, null)
                    }
                }
                "mp" -> {
                    if (msgParts.size == 3) {
                        val motorNumber = Integer.parseInt(msgParts[1])
                        val position = Integer.parseInt(msgParts[2].trim())
                        motors[motorNumber - 1].position = position
                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_POSITION_UPDATE, null, null)
                    }
                }
                "pr" -> {
                    if (msgParts.size == 3) {
                        val motorNumber = Integer.parseInt(msgParts[1])
                        val speed = Integer.parseInt(msgParts[2].trim())
                        motors[motorNumber - 1].speed = speed
                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_SPEED_UPDATE, null, null)
                    }
                }
                "ac" -> {
                    if (msgParts.size == 3) {
                        val motorNumber = Integer.parseInt(msgParts[1])
                        val acc = Integer.parseInt(msgParts[2].trim())
                        motors[motorNumber - 1].acc = acc
                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_ACC_UPDATE, null, null)
                    }
                }
                "cm" -> {
                    if (msgParts.size >= 2) {
                        when (Integer.parseInt(msgParts[1].removeSuffix("\r"))) {
                            0 -> customMode = MotionControllerCustomMode.NO_CUSTOM_MODE
                            1 -> {
                                if (msgParts.size == 6) {
                                    customMode = MotionControllerCustomMode.TIMELAPSE
                                }
                            }
                            2 -> {
                                if (msgParts.size == 10) {
                                    customMode = MotionControllerCustomMode.PANORAMA
                                }
                            }
                            3 -> {
                                if (msgParts.size == 10) {
                                    customMode = MotionControllerCustomMode.FOCUS_STACKING
                                }
                            }
                        }

                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE, null, null)
                    }
                }
                else -> {
                    sendIntentToActivity(EXTRA_MOTION_CONTROLLER_DATA, EXTRA_MOTION_CONTROLLER_MESSAGE, msg)
                }
            }
        }
    }

    fun sendCommand(msg: String) {
        try {
            sendBluetoothCommand(msg)
        } catch (ex: Exception) {
            isConnected = false
            Log.e("MotionControllerService", ex.message.toString())
            updateNotification(getString(R.string.disconnected), ConnectMenu::class.java as Class<AppCompatActivity>, null)
            sendIntentToActivity(EXTRA_MOTION_CONTROLLER_DISCONNECT, null, null)
        }
    }

    private fun sendIntentToActivity(topic: String, msgTopic: String?, msg: String?) {
        val intent = Intent(topic)
        if (msgTopic != null && msg != null) {
            intent.putExtra(msgTopic, msg)
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        Log.i("Destroy", "Bluetooth Motion")
    }
}
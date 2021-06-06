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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.motioncontroller.datasets.*
import kotlinx.android.synthetic.main.activity_main_page.*
import kotlin.time.ExperimentalTime

private const val CHANNEL_ID = "Motion_Controller_Service"
private const val NOTIFICATION_ID = 1

enum class MotionControllerCustomMode {
    NO_CUSTOM_MODE,
    TIMELAPSE,
    PANORAMA,
    FOCUS_STACKING,
    RESETTING,
}

val motorTypes = arrayListOf<MotorType>(
    object : MotorType {
        override val id: String = "nema_23_half_slider"
        override val name: String = "Nema 23 Slider"
        override val current: Double = 2.0
        override val microstepping: MotorTypeMicrostepping = MotorTypeMicrostepping.HALF
        override val operation: MotorTypeOperation = MotorTypeOperation.LINEAR
        override val operationSteps: Double = 100.0
        override val defaultSpeed: Int = 4500
    },
    object : MotorType {
        override val id: String = "nema_17_fourth_rotation"
        override val name: String = "Nema 17 Pan 27:1"
        override val current: Double = 0.8
        override val microstepping: MotorTypeMicrostepping = MotorTypeMicrostepping.QUARTER
        override val operation: MotorTypeOperation = MotorTypeOperation.ROTATION
        override val operationSteps: Double = 59.4
        override val defaultSpeed: Int = 1500
    }
)

@ExperimentalTime
@Suppress("UNCHECKED_CAST")
class MotionControllerService : BluetoothService() {
    private val binder = MotionControllerBinder()
    private val motors : List<MotorChannel> = List(4) { MotorChannel() }

    private var mHandler: Handler? = null
    private var jogModeState = 0
    private var jogModeMotor = 0
    private var jogModePower = 0
    private var jogModeStartSpeed = 0
    private var jogModeSpeed = 0

    private var notificationText = App.getContext().resources.getString(R.string.connecting)
    private var notificationActivity : Class<AppCompatActivity> = MainPage::class.java as Class<AppCompatActivity>
    private val notificationService = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(App.getContext().resources.getString(R.string.app_name))
        .setSmallIcon(R.drawable.ic_bluetooth_on)

    var init = false
    var motorCount = 0
    var version = ""
    var customMode = MotionControllerCustomMode.NO_CUSTOM_MODE
    var timelapseStatusData: TimelapseStatusData = TimelapseStatusData()

    companion object {
        const val EXTRA_MOTION_CONTROLLER_UPDATE = "motion-controller-update"
        const val EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE = "motion-controller-custom-mode-update"
        const val EXTRA_MOTION_CONTROLLER_SPEED_UPDATE: String = "motion-controller-speed-update"

        const val MOTION_CONTROLLER_POSITION_UPDATE_ACTION: String = "motion-controller-position-update-action"
        const val MOTION_CONTROLLER_POSITION_UPDATE_EXTRA: String = "motion-controller-position-update-extra"
        const val MOTION_CONTROLLER_DISCONNECT_ACTION: String = "motion-controller-disconnect-action"

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

    private fun createNotification(extraCustomMode: CustomModeType?): Notification {
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

    fun updateNotification(updateNotificationText: String?, updateNotificationActivity : Class<AppCompatActivity>?, extraCustomMode: CustomModeType?) {
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

    fun resetMotorSpeeds() {
        for (i in 1 .. motorCount) {
            setMotorSpeed(i, motors[i-1].motorType.defaultSpeed)
        }
    }

    @Suppress("DEPRECATION")
    fun jogModeEvent(motorNumber: Int, power: Int) {
        if (jogModeMotor != 0 && jogModeMotor != motorNumber) {
            stopJogMove(jogModeMotor)
            return
        }
        if (customMode != MotionControllerCustomMode.NO_CUSTOM_MODE || motorNumber < 0 || motorNumber > motorCount) {
            return
        }

        when (jogModeState) {
            0 -> {
                jogModeState = 2
                if (power == 0) {
                    jogModeState = 0
                    return
                }

                jogModeMotor = motorNumber
                jogModePower = power

                jogModeStartSpeed = motors[motorNumber - 1].motorType.defaultSpeed
                jogModeSpeed = jogModeSpeedForPower(power, jogModeStartSpeed)

                setMotorSpeed(jogModeMotor, jogModeSpeed)

                if (mHandler != null) {
                    mHandler = null
                }
                mHandler = Handler()
                jogModeMovement(jogModeMotor, power > 0)
            }
            1 -> {
                // Stopping
            }
            2 -> {
                if (power == 0) {
                    stopJogMove(jogModeMotor)
                    mHandler = null;
                }
                if ((jogModePower > 0 && power < 0) || (jogModePower < 0 && power > 0)) {
                    stopJogMove(jogModeMotor)
                    mHandler = null;
                }

                if (power != jogModePower) {
                    jogModePower = power
                    val newSpeed = jogModeSpeedForPower(power, jogModeStartSpeed)
                    if (newSpeed > jogModeSpeed) {
                        jogModeSpeed = newSpeed
                        setMotorSpeed(jogModeMotor, jogModeSpeed)
                    }
                }
            }
        }
    }

    private fun jogModeSpeedForPower(power: Int, speed: Int): Int {
        Math.abs(power).let {
            when {
                it < 8 -> {
                    return (speed * 0.1).toInt()
                }
                it < 16 -> {
                    return (speed * 0.3).toInt()
                }
                it < 24 -> {
                    return (speed * 0.6).toInt()
                }
                it < 33 -> {
                    return (speed * 1.0).toInt()
                }
                it < 42 -> {
                    return (speed * 1.2).toInt()
                }
                else -> {
                    return (speed * 1.3).toInt()
                }
            }
        }
    }

    private fun stopJogMove(motorNumber: Int) {
        jogModeState = 1
        sendCommand("sm $motorNumber")

        Handler().postDelayed({
            jogModeMotor = 0
            jogModeState = 0
            jogModePower = 0
            jogModeSpeed = 0
            jogModeStartSpeed = 0
        }, 500)
    }

    private fun jogModeMovement(motorNumber: Int, direction: Boolean) {
        val position = if (direction) "500000" else "-500000"

        if (jogModeState == 2) {
            sendCommand("jm $motorNumber $position")

            if (mHandler != null) {
                mHandler!!.postDelayed({ jogModeMovement(motorNumber, direction) }, 300)
            }
        }
    }

    fun goToPosition(motorNumber: Int, position: Int) {
        sendCommand("mm $motorNumber $position")
    }

    fun stopAllMotor() {
        sendCommand("sa")
        jogModeMotor = 0
        jogModeState = 0
    }

    fun requestInitData() {
        if (!init) {
            requestVersion()
        } else {
            sendIntentToActivity(EXTRA_MOTION_CONTROLLER_UPDATE, null, null)
            for (motorNumber in 1 .. motorCount) {
                sendIntentToActivity(
                    MOTION_CONTROLLER_POSITION_UPDATE_ACTION,
                    MOTION_CONTROLLER_POSITION_UPDATE_EXTRA,
                    motorNumber.toString()
                )
            }
            sendIntentToActivity(EXTRA_MOTION_CONTROLLER_SPEED_UPDATE, null, null)
            sendIntentToActivity(EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE, null, null)
        }
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

    fun setMotorSpeed(motorNumber: Int, speed: Int) {
        sendCommand("pr $motorNumber $speed")
    }

    fun startTimelapse(timelapseData: TimelapseData) {
        var command = ""
        val positionCount = timelapseData.motorData[0].positions.count()
        command += "tl ${timelapseData.interval} ${timelapseData.exposureTime} ${timelapseData.restTime} ${timelapseData.ramp} $positionCount"

        timelapseData.images.forEach { imagesEntry ->
            command += " $imagesEntry"
        }

        timelapseData.motorData.forEach { motorDataEntry ->
            command += " ${motorDataEntry.motorNumber}"
            for (i in 0 until positionCount) {
                command += " ${motorDataEntry.positions[i]}"
            }
        }

        sendCommand(command)
    }

    fun getMotorName(motorNumber: Int): String {
        return motors[motorNumber - 1].motorType.name
    }

    fun getMotorRealPosition(motorNumber: Int, steps: Int): String? {
        val motorType = motors[motorNumber - 1].motorType

        if (motorType.operation != null && motorType.operationSteps != null) {
            when (motorType.operation) {
                MotorTypeOperation.ROTATION -> {
                    return "${"%.1f".format((steps / motorType.operationSteps!!) % 360)}°"
                }
                MotorTypeOperation.LINEAR -> {
                    return "${"%.1f".format(steps / motorType.operationSteps!!)}mm"
                }
            }
        }

        return null
    }

    fun getMotorType(motorNumber: Int): MotorType? {
        return when (motorNumber) {
            1 -> {
                motorTypes.find { it -> it.id == "nema_23_half_slider" }
            }
            2 -> {
                motorTypes.find { it -> it.id == "nema_17_fourth_rotation" }
            }
            else -> null
        }
    }

    private fun initMotors() {
        val motorNumbers = (1 until (motorCount + 1) step 1)
        for (motorNumber in motorNumbers) {
            val motor = getMotorType(motorNumber)
            if (motor != null) {
                motors[motorNumber - 1].motorType = motor
            }

            setMotorSpeed(motorNumber, motors[motorNumber - 1].motorType.defaultSpeed)

            requestPostion(motorNumber)
            requestSpeed(motorNumber)
        }

        init = true
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

                        requestCustomMode()
                        initMotors()

                        sendIntentToActivity(EXTRA_MOTION_CONTROLLER_UPDATE, null, null)
                    }
                }
                "mp" -> {
                    if (msgParts.size == 3) {
                        val motorNumber = Integer.parseInt(msgParts[1])
                        val position = Integer.parseInt(msgParts[2].trim())
                        motors[motorNumber - 1].position = position
                        sendIntentToActivity(
                            MOTION_CONTROLLER_POSITION_UPDATE_ACTION,
                            MOTION_CONTROLLER_POSITION_UPDATE_EXTRA,
                            motorNumber.toString()
                        )
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
                "cm" -> {
                    if (msgParts.size >= 2) {
                        when (Integer.parseInt(msgParts[1].removeSuffix("\r"))) {
                            0 -> {
                                customMode = MotionControllerCustomMode.NO_CUSTOM_MODE
                                timelapseStatusData = TimelapseStatusData()
                            }
                            1 -> {
                                if (msgParts.size == 10) {
                                    customMode = MotionControllerCustomMode.TIMELAPSE
                                    timelapseStatusData.status = msgParts[2].toIntOrNull() ?: -1
                                    timelapseStatusData.execution = msgParts[3].toIntOrNull() ?: -1
                                    timelapseStatusData.currentImageCount =
                                        msgParts[4].toIntOrNull() ?: -1
                                    timelapseStatusData.images = msgParts[5].toIntOrNull() ?: -1
                                    timelapseStatusData.interval = msgParts[6].toIntOrNull() ?: -1
                                    timelapseStatusData.exposure = msgParts[7].toIntOrNull() ?: -1
                                    timelapseStatusData.rest = msgParts[8].toIntOrNull() ?: -1
                                    timelapseStatusData.ramp = msgParts[9].trim().toIntOrNull() ?: -1
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
                            10 -> {
                                customMode = MotionControllerCustomMode.RESETTING
                            }
                        }
                    }

                    sendIntentToActivity(EXTRA_MOTION_CONTROLLER_CUSTOM_MODE_UPDATE, null, null)
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
            sendIntentToActivity(MOTION_CONTROLLER_DISCONNECT_ACTION, null, null)
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
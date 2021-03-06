package com.example.motioncontroller

import IPickedNumber
import NumberPickerDialog
import NumberPickerType
import TAG_NUMBER_PICKER_DIALOG
import android.annotation.SuppressLint
import android.content.*
import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.motioncontroller.datasets.TimelapseData
import com.example.motioncontroller.datasets.TimelapseDataMotor
import kotlinx.android.synthetic.main.activity_custom_mode.*
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


enum class CustomModeType {
    JOG,
    TIMELAPSE,
    PANORAMA
}

const val CUSTOM_MODE_EXTRA = "CUSTOM_MODE_EXTRA"

@ExperimentalTime
class CustomMode : AppCompatActivity(), IPickedNumber {
    lateinit var mService: MotionControllerService

    var mBound: Boolean = false
    private var titleMessage = ""
    private lateinit var customMode: CustomModeType

    private var timelapseImagesTotal: Int = 0
    private var timelapseImagesPos2: Int = 0
    private var timelapseImagesPos3: Int = 0
    private var timelapseInterval: Int = 0
    private var timelapseExposure: Int = 0
    private var timelapseRest: Int = 0
    private var timelapseRamp: Int = 0
    private var timelapseEnabledPositions: IntArray = intArrayOf(1,0,0,1)
    private var timelapsePosition: Array<IntArray> = Array(4) { IntArray(4) { 0 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_mode)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val customModeExtra = intent?.getSerializableExtra(CUSTOM_MODE_EXTRA)
        if (customModeExtra != null) {
            customMode = customModeExtra as CustomModeType
        }
    }

    fun initJogModeOnCreate() {
        titleMessage = getString(R.string.jog_mode)
        supportActionBar?.setTitle(titleMessage)
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
            tv_jog_name_m1.text = mService.getMotorName(1)

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
        supportActionBar?.setTitle(titleMessage)
        cl_timelapse.visibility = View.VISIBLE

        val sharedPref = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE)

        timelapseImagesTotal = sharedPref.getInt(getString(R.string.pref_timelapse_images_total), 350)
        timelapseImagesPos2 = sharedPref.getInt(getString(R.string.pref_timelapse_images_pos2), 100)
        timelapseImagesPos3 = sharedPref.getInt(getString(R.string.pref_timelapse_images_pos3), 200)
        timelapseInterval = sharedPref.getInt(getString(R.string.pref_timelapse_interval), 8)
        timelapseExposure = sharedPref.getInt(getString(R.string.pref_timelapse_exposure), 4000)
        timelapseRest = sharedPref.getInt(getString(R.string.pref_timelapse_rest), 500)
        timelapseRamp = sharedPref.getInt(getString(R.string.pref_timelapse_ramp), 10)

        timelapsePosition[0][0] = sharedPref.getInt(getString(R.string.pref_timelapse_pos1_m1), 0)
        timelapsePosition[0][1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos1_m2), 0)
        timelapsePosition[0][2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos1_m3), 0)
        timelapsePosition[0][3] = sharedPref.getInt(getString(R.string.pref_timelapse_pos1_m4), 0)
        timelapsePosition[1][0] = sharedPref.getInt(getString(R.string.pref_timelapse_pos2_m1), 0)
        timelapsePosition[1][1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos2_m2), 0)
        timelapsePosition[1][2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos2_m3), 0)
        timelapsePosition[1][3] = sharedPref.getInt(getString(R.string.pref_timelapse_pos2_m4), 0)
        timelapsePosition[2][0] = sharedPref.getInt(getString(R.string.pref_timelapse_pos3_m1), 0)
        timelapsePosition[2][1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos3_m2), 0)
        timelapsePosition[2][2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos3_m3), 0)
        timelapsePosition[2][3] = sharedPref.getInt(getString(R.string.pref_timelapse_pos3_m4), 0)
        timelapsePosition[3][0] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m1), 0)
        timelapsePosition[3][1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m2), 0)
        timelapsePosition[3][2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m3), 0)
        timelapsePosition[3][3] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m4), 0)
        timelapseEnabledPositions[1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos2_enabled), 0)
        timelapseEnabledPositions[2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos3_enabled), 0)
        timelapsePosition[3][1] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m2), 0)
        timelapsePosition[3][2] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m3), 0)
        timelapsePosition[3][3] = sharedPref.getInt(getString(R.string.pref_timelapse_pos4_m4), 0)

        updateTimelapseUI()

        val imagesClick = View.OnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                val newFragment = NumberPickerDialog(
                    _title = "Images",
                    _subtitle = "Choose the amount of images",
                    _numberPickerType = NumberPickerType.IMAGES,
                    _initValue = timelapseImagesTotal,
                    _digits = 4,
                    _info = null
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }
        iv_timelapse_images.setOnClickListener(imagesClick)
        tv_timelapse_images.setOnClickListener(imagesClick)

        val intervalClick = View.OnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                val newFragment = NumberPickerDialog(
                    _title = "Interval",
                    _subtitle = "Choose the interval in seconds",
                    _numberPickerType = NumberPickerType.INTERVAL,
                    _initValue = timelapseInterval,
                    _digits = 3,
                    _info = "s"
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }
        iv_timelapse_interval.setOnClickListener(intervalClick)
        tv_timelapse_interval.setOnClickListener(intervalClick)

        val exposureClick = View.OnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                val newFragment = NumberPickerDialog(
                    _title = "Exposure",
                    _subtitle = "Choose the exposure time in milliseconds",
                    _numberPickerType = NumberPickerType.EXPOSURE,
                    _initValue = timelapseExposure,
                    _digits = 5,
                    _info = "ms"
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }
        iv_timelapse_exposure.setOnClickListener(exposureClick)
        tv_timelapse_exposure.setOnClickListener(exposureClick)

        val restClick = View.OnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                val newFragment = NumberPickerDialog(
                    _title = "Exposure",
                    _subtitle = "Choose the rest time in milliseconds",
                    _numberPickerType = NumberPickerType.REST,
                    _initValue = timelapseRest,
                    _digits = 4,
                    _info = "ms"
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }
        iv_timelapse_rest.setOnClickListener(restClick)
        tv_timelapse_rest.setOnClickListener(restClick)

        val rampClick = View.OnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                val newFragment = NumberPickerDialog(
                    _title = "Ramp",
                    _subtitle = "Choose the ramp for the timelapse moves between 0 and 40",
                    _numberPickerType = NumberPickerType.RAMP,
                    _initValue = timelapseRamp,
                    _digits = 2,
                    _info = "%"
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }
        iv_timelapse_ramp.setOnClickListener(rampClick)
        tv_timelapse_ramp.setOnClickListener(rampClick)

        tv_timelapse_pos2_images.setOnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE && timelapseEnabledPositions[1] == 1) {
                val newFragment = NumberPickerDialog(
                    _title = "Image Position 2",
                    _subtitle = "Choose the images until position 2",
                    _numberPickerType = NumberPickerType.POS2_IMAGES,
                    _initValue = timelapseImagesPos2,
                    _digits = 4,
                    _info = null
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }

        tv_timelapse_pos3_images.setOnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE && timelapseEnabledPositions[2] == 1) {
                val newFragment = NumberPickerDialog(
                    _title = "Image Position 3",
                    _subtitle = "Choose the images until position 3",
                    _numberPickerType = NumberPickerType.POS3_IMAGES,
                    _initValue = timelapseImagesPos3,
                    _digits = 4,
                    _info = null
                )
                newFragment.show(supportFragmentManager, TAG_NUMBER_PICKER_DIALOG)
            }
        }

        sb_timelapse_m1.setOnSeekBarChangeListener(jogModeSeekBar(1))
        sb_timelapse_m2.setOnSeekBarChangeListener(jogModeSeekBar(2))
        sb_timelapse_m3.setOnSeekBarChangeListener(jogModeSeekBar(3))
        sb_timelapse_m4.setOnSeekBarChangeListener(jogModeSeekBar(4))

        tv_timelapse_pos_1.setOnLongClickListener(setTimelapsePositionAll(0))
        tv_timelapse_pos_2.setOnLongClickListener(setTimelapsePositionAll(1))
        tv_timelapse_pos_3.setOnLongClickListener(setTimelapsePositionAll(2))
        tv_timelapse_pos_4.setOnLongClickListener(setTimelapsePositionAll(3))

        tv_timelapse_pos_2.setOnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                timelapseEnabledPositions[1] = when (timelapseEnabledPositions[1]) {
                    1 -> 0
                    else -> 1
                }

                updateTimelapseUI()
            }
        }
        tv_timelapse_pos_3.setOnClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                timelapseEnabledPositions[2] = when (timelapseEnabledPositions[2]) {
                    1 -> 0
                    else -> 1
                }

                updateTimelapseUI()
            }
        }
        tv_timelapse_pos_3.setOnLongClickListener(setTimelapsePositionAll(2))

        tv_timelapse_pos1_m1.setOnLongClickListener {
            setTimelapsePositionMotor(0, 1)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos1_m2.setOnLongClickListener {
            setTimelapsePositionMotor(0, 2)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos1_m3.setOnLongClickListener {
            setTimelapsePositionMotor(0, 3)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos1_m4.setOnLongClickListener {
            setTimelapsePositionMotor(0, 4)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos2_m1.setOnLongClickListener {
            setTimelapsePositionMotor(1, 1)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos2_m2.setOnLongClickListener {
            setTimelapsePositionMotor(1, 2)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos2_m3.setOnLongClickListener {
            setTimelapsePositionMotor(1, 3)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos2_m4.setOnLongClickListener {
            setTimelapsePositionMotor(1, 4)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos3_m1.setOnLongClickListener {
            setTimelapsePositionMotor(2, 1)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos3_m2.setOnLongClickListener {
            setTimelapsePositionMotor(2, 2)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos3_m3.setOnLongClickListener {
            setTimelapsePositionMotor(2, 3)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos3_m4.setOnLongClickListener {
            setTimelapsePositionMotor(2, 4)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos4_m1.setOnLongClickListener {
            setTimelapsePositionMotor(3, 1)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos4_m2.setOnLongClickListener {
            setTimelapsePositionMotor(3, 2)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos4_m3.setOnLongClickListener {
            setTimelapsePositionMotor(3, 3)
            updateTimelapseUI()
            true
        }
        tv_timelapse_pos4_m4.setOnLongClickListener {
            setTimelapsePositionMotor(3, 4)
            updateTimelapseUI()
            true
        }

        bt_timelapse_reset.setOnLongClickListener {
            if (mBound) {
                mService.resetCustomMode()
            }
            pb_timelapse.progress = 0
            tv_timelapse_info.text = ""

            true
        }

        bt_timelapse_stop_motor.setOnLongClickListener {
            if (mBound) {
                mService.stopAllMotor()
            }

            true
        }

        bt_timelapse_start.setOnLongClickListener {
            if (mBound) {
                mService.resetMotorSpeeds()

                var positions = IntArray(4)
                var images = IntArray(3) { 0 }
                var positionCount = 0

                positions[positionCount] = 0
                positionCount++

                if (timelapseEnabledPositions[1] == 1) {
                    positions[positionCount] = 1
                    images[positionCount - 1] = timelapseImagesPos2
                    positionCount++
                }
                if (timelapseEnabledPositions[2] == 1) {
                    positions[positionCount] = 2
                    images[positionCount - 1] = timelapseImagesPos3
                    positionCount++
                }

                images[positionCount - 1] = timelapseImagesTotal

                positions[positionCount] = 3
                positionCount++

                val timelapseData = object : TimelapseData {
                    override val images: IntArray = IntArray(positionCount - 1) {
                        images[it]
                    }
                    override val interval: Int = timelapseInterval
                    override val exposureTime: Int = timelapseExposure
                    override val restTime: Int = timelapseRest
                    override val ramp: Int = timelapseRamp
                    override val motorData: Array<TimelapseDataMotor> = Array(mService.motorCount) { motorNumberIt ->
                        object : TimelapseDataMotor {
                            override val motorNumber: Int = motorNumberIt + 1
                            override val positions: IntArray = IntArray(positionCount) { position ->
                                timelapsePosition[positions[position]][motorNumberIt]
                            }
                        }
                    }
                }

                mService.startTimelapse(timelapseData)
            }

            true
        }

        iv_timelapse_pos1_goto.setOnLongClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                mService.resetMotorSpeeds()
                mService.goToPosition(1,timelapsePosition[0][0])
                mService.goToPosition(2,timelapsePosition[0][1])
                mService.goToPosition(3,timelapsePosition[0][2])
                mService.goToPosition(4,timelapsePosition[0][3])
            }

            true
        }

        iv_timelapse_pos2_goto.setOnLongClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE && timelapseEnabledPositions[1] == 1) {
                mService.resetMotorSpeeds()
                mService.goToPosition(1,timelapsePosition[1][0])
                mService.goToPosition(2,timelapsePosition[1][1])
                mService.goToPosition(3,timelapsePosition[1][2])
                mService.goToPosition(4,timelapsePosition[1][3])
            }

            true
        }

        iv_timelapse_pos3_goto.setOnLongClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE && timelapseEnabledPositions[2] == 1) {
                mService.resetMotorSpeeds()
                mService.goToPosition(1,timelapsePosition[2][0])
                mService.goToPosition(2,timelapsePosition[2][1])
                mService.goToPosition(3,timelapsePosition[2][2])
                mService.goToPosition(4,timelapsePosition[2][3])
            }

            true
        }

        iv_timelapse_pos4_goto.setOnLongClickListener {
            if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
                mService.resetMotorSpeeds()
                mService.goToPosition(1,timelapsePosition[3][0])
                mService.goToPosition(2,timelapsePosition[3][1])
                mService.goToPosition(3,timelapsePosition[3][2])
                mService.goToPosition(4,timelapsePosition[3][3])
            }

            true
        }

        if (mBound) {
            val motorCount = mService.motorCount

            if (motorCount >= 2) {
                tv_timelapse_m2.visibility = View.VISIBLE
                tv_timelapse_position_m2.visibility = View.VISIBLE
                tv_timelapse_pos1_m2.visibility = View.VISIBLE
                tv_timelapse_pos2_m2.visibility = View.VISIBLE
                tv_timelapse_pos3_m2.visibility = View.VISIBLE
                tv_timelapse_pos4_m2.visibility = View.VISIBLE
                sb_timelapse_m2.visibility = View.VISIBLE
            }
            if (motorCount >= 3) {
                tv_timelapse_m3.visibility = View.VISIBLE
                tv_timelapse_position_m3.visibility = View.VISIBLE
                tv_timelapse_pos1_m3.visibility = View.VISIBLE
                tv_timelapse_pos2_m3.visibility = View.VISIBLE
                tv_timelapse_pos3_m3.visibility = View.VISIBLE
                tv_timelapse_pos4_m3.visibility = View.VISIBLE
                sb_timelapse_m3.visibility = View.VISIBLE
            }
            if (motorCount >= 4) {
                tv_timelapse_m4.visibility = View.VISIBLE
                tv_timelapse_position_m4.visibility = View.VISIBLE
                tv_timelapse_pos1_m4.visibility = View.VISIBLE
                tv_timelapse_pos2_m4.visibility = View.VISIBLE
                tv_timelapse_pos3_m4.visibility = View.VISIBLE
                tv_timelapse_pos4_m4.visibility = View.VISIBLE
                sb_timelapse_m4.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTimelapseUI() {
        tv_timelapse_images.text = "$timelapseImagesTotal"
        tv_timelapse_interval.text = "${timelapseInterval}s"
        tv_timelapse_exposure.text = "${timelapseExposure}ms"
        tv_timelapse_rest.text = "${timelapseRest}ms"
        tv_timelapse_ramp.text = "${timelapseRamp}%"

        tv_timelapse_duration.text = (timelapseImagesTotal * timelapseInterval).seconds.toString()
        tv_timelapse_duration.text = "${formatDuration((timelapseImagesTotal * timelapseInterval).seconds)}"
        tv_timelapse_video_time.text = "${(timelapseImagesTotal/24)}s/${(timelapseImagesTotal/30)}s"

        tv_timelapse_pos1_images.text = "1"
        tv_timelapse_pos2_images.text = "$timelapseImagesPos2"
        tv_timelapse_pos3_images.text = "$timelapseImagesPos3"
        tv_timelapse_pos4_images.text = "$timelapseImagesTotal"
        tv_timelapse_pos1_m1.text = mService.getMotorRealPosition(1, timelapsePosition[0][0]) ?: timelapsePosition[0][0].toString()
        tv_timelapse_pos1_m2.text = mService.getMotorRealPosition(2, timelapsePosition[0][1]) ?: timelapsePosition[0][1].toString()
        tv_timelapse_pos1_m3.text = mService.getMotorRealPosition(3, timelapsePosition[0][2]) ?: timelapsePosition[0][2].toString()
        tv_timelapse_pos1_m4.text = mService.getMotorRealPosition(4, timelapsePosition[0][3]) ?: timelapsePosition[0][3].toString()
        tv_timelapse_pos2_m1.text = mService.getMotorRealPosition(1, timelapsePosition[1][0]) ?: timelapsePosition[1][0].toString()
        tv_timelapse_pos2_m2.text = mService.getMotorRealPosition(2, timelapsePosition[1][1]) ?: timelapsePosition[1][1].toString()
        tv_timelapse_pos2_m3.text = mService.getMotorRealPosition(3, timelapsePosition[1][2]) ?: timelapsePosition[1][2].toString()
        tv_timelapse_pos2_m4.text = mService.getMotorRealPosition(4, timelapsePosition[1][3]) ?: timelapsePosition[1][3].toString()
        tv_timelapse_pos3_m1.text = mService.getMotorRealPosition(1, timelapsePosition[2][0]) ?: timelapsePosition[2][0].toString()
        tv_timelapse_pos3_m2.text = mService.getMotorRealPosition(2, timelapsePosition[2][1]) ?: timelapsePosition[2][1].toString()
        tv_timelapse_pos3_m3.text = mService.getMotorRealPosition(3, timelapsePosition[2][2]) ?: timelapsePosition[2][2].toString()
        tv_timelapse_pos3_m4.text = mService.getMotorRealPosition(4, timelapsePosition[2][3]) ?: timelapsePosition[2][3].toString()
        tv_timelapse_pos4_m1.text = mService.getMotorRealPosition(1, timelapsePosition[3][0]) ?: timelapsePosition[3][0].toString()
        tv_timelapse_pos4_m2.text = mService.getMotorRealPosition(2, timelapsePosition[3][1]) ?: timelapsePosition[3][1].toString()
        tv_timelapse_pos4_m3.text = mService.getMotorRealPosition(3, timelapsePosition[3][2]) ?: timelapsePosition[3][2].toString()
        tv_timelapse_pos4_m4.text = mService.getMotorRealPosition(4, timelapsePosition[3][3]) ?: timelapsePosition[3][3].toString()

        if (timelapseEnabledPositions[1] == 0) {
            tv_timelapse_pos_2.apply {
                paintFlags = paintFlags or STRIKE_THRU_TEXT_FLAG
            }
            tv_timelapse_pos2_images.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos2_m1.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos2_m2.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos2_m3.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos2_m4.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
        } else {
            tv_timelapse_pos_2.apply {
                paintFlags = paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
            }
            tv_timelapse_pos2_images.setTextColor(ContextCompat.getColor(this, R.color.black));
            tv_timelapse_pos2_m1.setTextColor(ContextCompat.getColor(this, R.color.orange));
            tv_timelapse_pos2_m2.setTextColor(ContextCompat.getColor(this, R.color.purple_200));
            tv_timelapse_pos2_m3.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
            tv_timelapse_pos2_m4.setTextColor(ContextCompat.getColor(this, R.color.teal_200));
        }

        if (timelapseEnabledPositions[2] == 0) {
            tv_timelapse_pos_3.apply {
                paintFlags = paintFlags or STRIKE_THRU_TEXT_FLAG
            }
            tv_timelapse_pos3_images.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos3_m1.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos3_m2.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos3_m3.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
            tv_timelapse_pos3_m4.setTextColor(ContextCompat.getColor(this, R.color.light_grey));
        } else {
            tv_timelapse_pos_3.apply {
                paintFlags = paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
            }
            tv_timelapse_pos3_images.setTextColor(ContextCompat.getColor(this, R.color.black));
            tv_timelapse_pos3_m1.setTextColor(ContextCompat.getColor(this, R.color.orange));
            tv_timelapse_pos3_m2.setTextColor(ContextCompat.getColor(this, R.color.purple_200));
            tv_timelapse_pos3_m3.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
            tv_timelapse_pos3_m4.setTextColor(ContextCompat.getColor(this, R.color.teal_200));
        }
    }

    fun formatDuration(duration: Duration): String? {
        val seconds = duration.inSeconds.toInt()
        val absSeconds = abs(seconds)
        return String.format(
            "%02d:%02d:%02d",
            absSeconds / 3600,
            absSeconds % 3600 / 60,
            absSeconds % 60
        )
    }

    override fun onNumberPicked(numberPickerType: NumberPickerType, value: Int) {
        when(numberPickerType) {
            NumberPickerType.IMAGES -> {
                timelapseImagesTotal = value
            }
            NumberPickerType.INTERVAL -> {
                timelapseInterval = value
            }
            NumberPickerType.EXPOSURE -> {
                timelapseExposure = value
            }
            NumberPickerType.REST -> {
                timelapseRest = value
            }
            NumberPickerType.RAMP -> {
                timelapseRamp = if (value > 40) 40 else value
            }
            NumberPickerType.POS2_IMAGES -> {
                timelapseImagesPos2 = value
            }
            NumberPickerType.POS3_IMAGES -> {
                timelapseImagesPos3 = value
            }
        }

        updateTimelapseUI()
    }

    private fun setTimelapsePositionAll(position: Int): View.OnLongClickListener = View.OnLongClickListener {
        if (mBound) {
            setTimelapsePositionMotor(position, 1)

            if (mService.motorCount >= 2) {
                setTimelapsePositionMotor(position, 2)
            }
            if (mService.motorCount >= 3) {
                setTimelapsePositionMotor(position, 3)
            }
            if (mService.motorCount >= 4) {
                setTimelapsePositionMotor(position, 4)
            }
        }

        updateTimelapseUI()
        true
    }

    private fun setTimelapsePositionMotor(position: Int, motorNumber: Int) {
        if (mBound && mService.customMode == MotionControllerCustomMode.NO_CUSTOM_MODE) {
            timelapsePosition[position][motorNumber - 1] = mService.getMotorPosition(motorNumber)
        }
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
        storeCustomModeData()
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

        storeCustomModeData()

        super.onPause()
    }

    private fun storeCustomModeData() {
        val sharedPref = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE).edit()
        when (customMode) {
            CustomModeType.JOG -> {

            }
            CustomModeType.TIMELAPSE -> {
                sharedPref.putInt(getString(R.string.pref_timelapse_images_total), timelapseImagesTotal)
                sharedPref.putInt(getString(R.string.pref_timelapse_images_pos2), timelapseImagesPos2)
                sharedPref.putInt(getString(R.string.pref_timelapse_images_pos3), timelapseImagesPos3)
                sharedPref.putInt(getString(R.string.pref_timelapse_interval), timelapseInterval)
                sharedPref.putInt(getString(R.string.pref_timelapse_exposure), timelapseExposure)
                sharedPref.putInt(getString(R.string.pref_timelapse_rest), timelapseRest)
                sharedPref.putInt(getString(R.string.pref_timelapse_ramp), timelapseRamp)
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m1), timelapsePosition[0][0])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m2), timelapsePosition[0][1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m3), timelapsePosition[0][2])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m4), timelapsePosition[0][3])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m1), timelapsePosition[0][0])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m2), timelapsePosition[0][1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m3), timelapsePosition[0][2])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos1_m4), timelapsePosition[0][3])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos2_m1), timelapsePosition[1][0])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos2_m2), timelapsePosition[1][1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos2_m3), timelapsePosition[1][2])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos2_m4), timelapsePosition[1][3])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos3_m1), timelapsePosition[2][0])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos3_m2), timelapsePosition[2][1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos3_m3), timelapsePosition[2][2])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos3_m4), timelapsePosition[2][3])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos4_m1), timelapsePosition[3][0])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos4_m2), timelapsePosition[3][1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos4_m3), timelapsePosition[3][2])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos4_m4), timelapsePosition[3][3])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos2_enabled), timelapseEnabledPositions[1])
                sharedPref.putInt(getString(R.string.pref_timelapse_pos3_enabled), timelapseEnabledPositions[2])
            }
        }
        sharedPref.commit()
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
            if (mBound) {
                val customModeController = mService.customMode
                when (customModeController) {
                    MotionControllerCustomMode.RESETTING -> {
                        when (customMode) {
                            CustomModeType.TIMELAPSE -> {
                                tv_timelapse_info.text = "Resetting"
                                updateTimelapseUI()
                            }
                        }
                    }
                    MotionControllerCustomMode.NO_CUSTOM_MODE -> {
                        when (customMode) {
                            CustomModeType.TIMELAPSE -> {
                                pb_timelapse.progress = 0
                                tv_timelapse_info.text = ""
                                bt_timelapse_start.isEnabled = true
                                bt_timelapse_reset.isEnabled = false
                                iv_timelapse_pos1_goto.visibility = View.VISIBLE
                                iv_timelapse_pos2_goto.visibility = View.VISIBLE
                                iv_timelapse_pos3_goto.visibility = View.VISIBLE
                                iv_timelapse_pos4_goto.visibility = View.VISIBLE
                                sb_timelapse_m1.isEnabled = true
                                sb_timelapse_m2.isEnabled = true
                                sb_timelapse_m3.isEnabled = true
                                sb_timelapse_m4.isEnabled = true
                                tv_timelapse_images.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_duration.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_video_time.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_interval.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_exposure.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_rest.setTextColor(ContextCompat.getColor(context, R.color.black));
                                tv_timelapse_ramp.setTextColor(ContextCompat.getColor(context, R.color.black));
                            }
                        }
                    }
                    MotionControllerCustomMode.TIMELAPSE -> {
                        if (customMode == CustomModeType.TIMELAPSE) {
                            val timelapseStatus = mService.timelapseStatusData

                            if (timelapseStatus.images != -1) {
                                timelapseImagesTotal = timelapseStatus.images
                            }
                            if (timelapseStatus.interval != -1) {
                                timelapseInterval = timelapseStatus.interval
                            }
                            if (timelapseStatus.exposure != -1) {
                                timelapseExposure = timelapseStatus.exposure
                            }
                            if (timelapseStatus.rest != -1) {
                                timelapseRest = timelapseStatus.rest
                            }
                            if (timelapseStatus.rest != -1) {
                                timelapseRamp = timelapseStatus.ramp
                            }

                            when (timelapseStatus.status) {
                                0 -> {
                                    pb_timelapse.progress = 0
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_setup)
                                }
                                1 -> {
                                    var text = "${getString(R.string.timelapse_status_running)}: "
                                    when (timelapseStatus.execution) {
                                        0 -> text += getString(R.string.timelapse_status_execution_init)
                                        else -> text += "${timelapseStatus.currentImageCount}/${timelapseStatus.images}"
                                    }
                                    tv_timelapse_info.text = text
                                    pb_timelapse.progress =
                                        ((timelapseStatus.currentImageCount.toDouble() * 100) / timelapseStatus.images.toDouble()).toInt()
                                }
                                2 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_done)
                                    pb_timelapse.progress = 100
                                }
                                10 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_10)
                                }
                                11 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_11)
                                }
                                12 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_12)
                                }
                                13 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_13)
                                }
                                14 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_14)
                                }
                                15 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_15)
                                }
                                16 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_16)
                                }
                                17 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_17)
                                }
                                18 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_18)
                                }
                                19 -> {
                                    tv_timelapse_info.text =
                                        getString(R.string.timelapse_status_error_19)
                                }
                            }

                            updateTimelapseUI()

                            bt_timelapse_start.isEnabled = false
                            bt_timelapse_reset.isEnabled = true
                            iv_timelapse_pos1_goto.visibility = View.GONE
                            iv_timelapse_pos2_goto.visibility = View.GONE
                            iv_timelapse_pos3_goto.visibility = View.GONE
                            iv_timelapse_pos4_goto.visibility = View.GONE
                            sb_timelapse_m1.isEnabled = false
                            sb_timelapse_m2.isEnabled = false
                            sb_timelapse_m3.isEnabled = false
                            sb_timelapse_m4.isEnabled = false
                            tv_timelapse_images.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_duration.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_video_time.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_interval.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_exposure.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_rest.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                            tv_timelapse_ramp.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
                        }
                    }
                }
            }
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
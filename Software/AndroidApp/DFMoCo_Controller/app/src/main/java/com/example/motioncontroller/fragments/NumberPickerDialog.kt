import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.NumberPicker.OnValueChangeListener
import androidx.fragment.app.DialogFragment
import com.example.motioncontroller.R
import kotlinx.android.synthetic.main.dialog_number_picker.*
import kotlinx.android.synthetic.main.dialog_number_picker.view.*
import kotlin.math.pow


interface IPickedNumber {
    fun onNumberPicked(numberPickerType: NumberPickerType, value: Int)
}

enum class NumberPickerType {
    IMAGES,
    INTERVAL,
    EXPOSURE,
    REST
}

public const val TAG_NUMBER_PICKER_DIALOG = "NUMBER_PICKER_DIALOG"

class NumberPickerDialog(_title: String, _subtitle: String, _numberPickerType: NumberPickerType, _initValue: Int, _digits: Int, _info: String?) : DialogFragment() {
    private val title = _title
    private val subtitle = _subtitle
    private val initValue = _initValue
    private val digits = if (initValue.toString().length <= _digits) _digits else initValue.toString().length
    private val info = _info
    private val numberPickerType = _numberPickerType
    private var mCallback: IPickedNumber? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_number_picker, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            mCallback = activity as IPickedNumber
        } catch (e: ClassCastException) {
            Log.d("NumberPickerDialog", "Activity doesn't implement the IPickedNumber interface")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (info != null) {
            tv_numberpicker_info.text = info
        } else {
            tv_numberpicker_info.visibility = View.GONE
        }

        tv_numberpicker_title.text = title
        tv_numberpicker_subtitle.text = subtitle

        for (i in 1..digits) {
            val value = (initValue / ((10.0).pow(i - 1))).toInt() % 10

            when (i) {
                1 -> {
                    np_numberpicker_1.minValue = 0
                    np_numberpicker_1.maxValue = 9
                    np_numberpicker_1.value = value
                    np_numberpicker_1.visibility = View.VISIBLE
                }
                2-> {
                    np_numberpicker_2.minValue = 0
                    np_numberpicker_2.maxValue = 9
                    np_numberpicker_2.value = value
                    np_numberpicker_2.visibility = View.VISIBLE

                }
                3 -> {
                    np_numberpicker_3.minValue = 0
                    np_numberpicker_3.maxValue = 9
                    np_numberpicker_3.value = value
                    np_numberpicker_3.visibility = View.VISIBLE

                }
                4 -> {
                    np_numberpicker_4.minValue = 0
                    np_numberpicker_4.maxValue = 9
                    np_numberpicker_4.value = value
                    np_numberpicker_4.visibility = View.VISIBLE

                }
                5 -> {
                    np_numberpicker_5.minValue = 0
                    np_numberpicker_5.maxValue = 9
                    np_numberpicker_5.value = value
                    np_numberpicker_5.visibility = View.VISIBLE

                }
            }
        }

        view.bt_numberpicker_ok.setOnClickListener {
            mCallback?.onNumberPicked(numberPickerType, getValue())
            dismiss()
        }

        view.bt_numberpicker_cancel.setOnClickListener {
            dismiss()
        }
    }

    fun getValue(): Int {
        return np_numberpicker_5.value * 10000 +
                np_numberpicker_4.value * 1000 +
                 np_numberpicker_3.value * 100 +
                  np_numberpicker_2.value * 10 +
                   np_numberpicker_1.value
    }
}
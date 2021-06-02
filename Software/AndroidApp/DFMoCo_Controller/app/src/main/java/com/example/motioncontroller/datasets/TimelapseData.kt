package com.example.motioncontroller.datasets

interface TimelapseDataMotor {
    val motorNumber: Int
    val positions: IntArray
}

interface TimelapseData {
    val images: IntArray
    val interval: Int
    val exposureTime: Int
    val restTime: Int

    val motorData: Array<TimelapseDataMotor>

    fun validate(): Boolean {
        var size: Int? = null
        return motorData.all { motorDataEntry ->
            if (size == null) {
                size = motorDataEntry.positions.size
            } else if (size != motorDataEntry.positions.size) {
                false
            }

            true
        }
    }
}

class TimelapseStatusData {
    var status: Int = -1
    var execution: Int = -1
    var images: Int = -1
    var interval: Int = -1
    var exposure: Int = -1
    var rest: Int = -1
    var currentImageCount: Int = -1
}
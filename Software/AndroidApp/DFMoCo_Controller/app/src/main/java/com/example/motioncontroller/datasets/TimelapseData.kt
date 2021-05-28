package com.example.motioncontroller.datasets

interface TimelapseDataMotor {
    val motorNumber: Int
    val positions: IntArray
}

interface TimelapseData {
    val images: Int
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
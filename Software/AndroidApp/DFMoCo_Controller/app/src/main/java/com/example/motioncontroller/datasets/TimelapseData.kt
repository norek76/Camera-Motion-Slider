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
    val ramp: Int

    val motorData: Array<TimelapseDataMotor>
}

class TimelapseStatusData {
    var status: Int = -1
    var execution: Int = -1
    var images: Int = -1
    var interval: Int = -1
    var exposure: Int = -1
    var rest: Int = -1
    var ramp: Int = -1
    var currentImageCount: Int = -1
}
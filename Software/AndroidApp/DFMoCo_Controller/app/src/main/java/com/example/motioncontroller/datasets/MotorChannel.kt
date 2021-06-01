package com.example.motioncontroller.datasets

class MotorChannel {
    var position = 0;
    var speed = 0;
    var motorType: MotorType = object : MotorType {
        override val id: String = "default"
        override val name: String = "Default Motor"
        override val current: Double = 0.5
        override val microstepping: MotorTypeMicrostepping = MotorTypeMicrostepping.QUARTER
        override val operation: MotorTypeOperation? = null
        override val operationSteps: Double? = null
        override val defaultSpeed: Int = 1500
    }
}
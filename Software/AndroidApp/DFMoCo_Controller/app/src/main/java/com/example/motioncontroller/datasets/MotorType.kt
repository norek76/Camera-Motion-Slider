package com.example.motioncontroller.datasets

enum class MotorTypeOperation {
    LINEAR,
    ROTATION
}
enum class MotorTypeMicrostepping {
    FULL,
    HALF,
    QUARTER,
    SIXTEENTH
}

interface MotorType {
    val id: String
    val name: String
    val current: Double
    val microstepping: MotorTypeMicrostepping
    val operation: MotorTypeOperation
    val operationSteps: Double // Linear = x Steps / mm; Rotation = x Steps / °
    val defaultSpeed: Int
}
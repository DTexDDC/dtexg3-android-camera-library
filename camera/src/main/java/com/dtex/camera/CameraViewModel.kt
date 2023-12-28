package com.dtex.camera

import android.hardware.SensorManager
import android.net.Uri
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    var assetFileName: String? = null

    var photoUri: Uri? = null

    // Config
    val detectionConfidence = 0.7
    private val rotationConfidence = 1.0
    private val accelerationLimit = 0.3

    // TensorFlow Lite
    val inputSize = 512
    val detectionsSize = 25
    var isProcessing = false

    // Sensor
    val rotationMatrix = FloatArray(9)
    val rotationResult = FloatArray(3)

    var accel: Float = 0.0f
    var accelCurrent: Float = SensorManager.GRAVITY_EARTH
    var accelLast: Float = SensorManager.GRAVITY_EARTH

    // Status
    val isAcceptable: Boolean
        get() = orientation == 0 && isBoundingDetected && rotation > rotationConfidence && acceleration < accelerationLimit
    var isBoundingDetected: Boolean = false
    var orientation: Int = 0    // Device orientation
    var rotation: Float = 0.0f  // [-π/2, π/2]
    var acceleration: Float = 0.0f

    var lastAcceptable: Boolean = false
}
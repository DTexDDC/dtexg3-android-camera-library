package com.dtex.camera

import android.net.Uri
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    var assetFileName: String? = null

    var photoUri: Uri? = null

    // Status
    val isAcceptable: Boolean
        get() = orientation == 0 && isBoundingDetected && rotation > 0.5 && acceleration < 3
    var isBoundingDetected: Boolean = false
    var orientation: Int = 0    // Device orientation
    var rotation: Float = 0.0f  // [-π/2, π/2]
    var acceleration: Float = 0.0f
}
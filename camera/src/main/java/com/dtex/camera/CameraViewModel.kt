package com.dtex.camera

import android.net.Uri
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    var assetFileName: String? = null

    var photoUri: Uri? = null

    // Status
    var isBoundingDetected: Boolean = false
    var rotation: Float = 0.0f  // [-π/2, π/2]
    var acceleration: Float = 0.0f
}
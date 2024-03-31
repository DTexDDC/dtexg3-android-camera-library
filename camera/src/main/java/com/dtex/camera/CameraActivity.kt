package com.dtex.camera

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class CameraActivity : AppCompatActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Load bundle
        viewModel.assetFileName = intent.getStringExtra(DtexCamera.ARG_MODEL_FILE_NAME)
        intent.getDoubleExtra(DtexCamera.ARG_DETECTION_CONFIDENCE, -1.0).let {
            if (it > 0) {
                viewModel.detectionConfidence = it
            }
        }
    }
}
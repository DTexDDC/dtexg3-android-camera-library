package com.dtex.cameraexample

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dtex.camera.DtexCamera
import com.dtex.cameraexample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val photoUri: Uri? = DtexCamera.getPhotoUri(data)
                binding.imageView.setImageURI(photoUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraButton.setOnClickListener {
            DtexCamera.with(this)
                .modelFile("model.tflite")
                .createIntent { intent ->
                    cameraLauncher.launch(intent)
                }
        }
    }
}
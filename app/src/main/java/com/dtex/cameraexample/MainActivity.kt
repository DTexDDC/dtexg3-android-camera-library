package com.dtex.cameraexample

import android.app.Activity
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
                val res = DtexCamera.getResult(data)
                binding.imageView.setImageURI(res?.photoUri)
                binding.resultTextView.text =
                    String.format("Status: %s", if (res?.isAcceptable == true) "Green" else "Red")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraButton.setOnClickListener {
            DtexCamera.with(this)
                .modelFile("shelf.tflite")
                .createIntent { intent ->
                    cameraLauncher.launch(intent)
                }
        }
    }
}
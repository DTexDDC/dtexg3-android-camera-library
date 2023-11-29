package com.dtex.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.dtex.camera.databinding.FragmentCameraBinding
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted = permissions.entries.all { it.value }
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT)
                    .show()
                // Finish activity
                activity?.finish()
            }
        }

    private val viewModel: CameraViewModel by activityViewModels()
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var interpreter: Interpreter
    private val inputSize = 512
    private val detectionsSize = 25
    private var isProcessing = false

    private val previewWidth: Int by lazy {
        resources.displayMetrics.widthPixels
    }
    private val previewHeight: Int by lazy {
        (previewWidth / 3) * 4
    }

    private lateinit var canvas: Canvas
    private lateinit var paint: Paint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure TensorFlow model
        try {
            val fileDescriptor = requireContext().assets.openFd("aldi.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val byteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
            interpreter = Interpreter(byteBuffer)
            interpreter.allocateTensors()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
        }

        if (hasPermissions()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.captureButton.setOnClickListener { takePhoto() }

        binding.canvasImageView.doOnLayout {
            val width = it.measuredWidth
            val height = it.measuredHeight

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            canvas = Canvas(bitmap)

            paint = Paint()
            paint.color = Color.parseColor("#00DD00")
            paint.strokeWidth = 5F

            binding.canvasImageView.setImageBitmap(bitmap)
        }

        viewModel.isDetected.observe(viewLifecycleOwner) {
            binding.detectionStatusView.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (it) R.color.green else R.color.red
                )
            )
        }
    }

    private fun hasPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (isProcessing) return@setAnalyzer
                        processFrame(image)
                    }
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processFrame(image: ImageProxy) {
        isProcessing = true
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Convert image to bitmap
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            image.use { bitmap.copyPixelsFromBuffer(image.planes[0].buffer) }
            // Preprocess image to input tensor size
            val imageRotation = image.imageInfo.rotationDegrees
            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .build()
            // Create inputs and outputs and invoke model
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
            val inputs = arrayOf(tensorImage.buffer)
            val scores = arrayOf(FloatArray(detectionsSize))
            val boundingBoxes = arrayOf(Array(detectionsSize) { FloatArray(4) })
            val detectionCount = FloatArray(1)
            val categories = arrayOf(FloatArray(detectionsSize))
            val outputs = mapOf(
                0 to scores,
                1 to boundingBoxes,
                2 to detectionCount,
                3 to categories
            )
            interpreter.runForMultipleInputsOutputs(inputs, outputs)
            // Return outputs
            Log.d(TAG, "scores: " + scores[0])
            Log.d(TAG, "boundingBoxes: " + processBoundingBoxes(boundingBoxes[0]))
            Log.d(TAG, "detectionCount: " + detectionCount[0].toInt())
            Log.d(TAG, "categories: " + categories[0])

            val maxScoreIndex = scores[0].indices.maxBy { scores[0][it] }
            val score = scores[0][maxScoreIndex]
            val boundingBox = processBoundingBoxes(boundingBoxes[0])[maxScoreIndex]
            val x = boundingBox["x"]!!.toFloat() * previewWidth
            val y = boundingBox["y"]!!.toFloat() * previewHeight
            val width = boundingBox["width"]!!.toFloat() * previewWidth
            val height = boundingBox["height"]!!.toFloat() * previewHeight

            if (score > 0.5) {
                viewModel.isDetected.postValue(true)
                canvas.drawLine(x, y, x + width, y, paint)
                canvas.drawLine(x + width, y, x + width, y + height, paint)
                canvas.drawLine(x + width, y + height, x, y + height, paint)
                canvas.drawLine(x, y + height, x, y, paint)
                binding.canvasImageView.invalidate()
            } else {
                viewModel.isDetected.postValue(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        image.close()
        isProcessing = false
    }

    private fun processBoundingBoxes(input: Array<FloatArray>): MutableList<MutableMap<String, Double>> {
        val out = mutableListOf<MutableMap<String, Double>>()
        for (bb in input) {
            val map = mutableMapOf<String, Double>().apply {
                put("x", bb[1].toDouble())
                put("y", bb[0].toDouble())
                put("width", bb[3].toDouble() - bb[1].toDouble())
                put("height", bb[2].toDouble() - bb[0].toDouble())
            }
            out.add(map)
        }
        return out
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val cacheDir = requireContext().cacheDir
        val fileName = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val outputFile = File(cacheDir, "$fileName.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(TAG, msg)
                    viewModel.photoUri = output.savedUri
                    // Navigate to Review
                    parentFragmentManager.commit {
                        setReorderingAllowed(true)
                        setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                        )
                        add(
                            R.id.fragment_container,
                            ReviewFragment(),
                            ReviewFragment.TAG
                        )
                        addToBackStack(ReviewFragment.TAG)
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}
package com.dtex.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.core.graphics.drawable.toDrawable
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
import kotlin.math.sqrt

class CameraFragment : Fragment(), SensorEventListener {

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

    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            binding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            binding.overlay.postDelayed({
                // Remove white flash animation
                binding.overlay.background = null
            }, 50L)
        }
    }

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val rotationResult = FloatArray(3)
    private var accelerometerSensor: Sensor? = null
    private var accel: Float = 0.0f
    private var accelCurrent: Float = SensorManager.GRAVITY_EARTH
    private var accelLast: Float = SensorManager.GRAVITY_EARTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasPermissions()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
        configureModel()
        binding.captureButton.setOnClickListener { takePhoto() }

        binding.canvasImageView.doOnLayout {
            val width = it.measuredWidth
            val height = it.measuredHeight

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            canvas = Canvas(bitmap)

            paint = Paint()
            paint.strokeWidth = 5F

            binding.canvasImageView.setImageBitmap(bitmap)
        }
    }

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometerSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    /**
     * Configure TensorFlow model
     * */
    private fun configureModel() {
        viewModel.assetFileName?.let { assetFileName ->
            try {
                val fileDescriptor = requireContext().assets.openFd(assetFileName)
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
        } ?: run {
            Toast.makeText(requireContext(), "Model file is not provided", Toast.LENGTH_SHORT)
                .show()
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

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            viewModel.isBoundingDetected = false
            scores[0].withIndex()
                .groupBy { categories[0][it.index] }
                .map { it.value }
                .sortedWith { a, b ->
                    b.maxOf { it.value }.compareTo(a.maxOf { it.value })
                }
                .take(5)
                .forEachIndexed { groupIndex, groupValue ->
                    groupValue
                        .filter { it.value > 0.1 }
                        .sortedWith { a, b ->
                            b.value.compareTo(a.value)
                        }
                        .take(5)
                        .forEach {
                            viewModel.isBoundingDetected = true

                            paint.color = boundingColors[groupIndex]
                            val x1 = boundingBoxes[0][it.index][1] * previewWidth
                            val y1 = boundingBoxes[0][it.index][0] * previewHeight
                            val x2 = boundingBoxes[0][it.index][3] * previewWidth
                            val y2 = boundingBoxes[0][it.index][2] * previewHeight
                            canvas.drawLine(x1, y1, x2, y1, paint)
                            canvas.drawLine(x2, y1, x2, y2, paint)
                            canvas.drawLine(x2, y2, x1, y2, paint)
                            canvas.drawLine(x1, y2, x1, y1, paint)
                        }
                }
            binding.canvasImageView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        image.close()
        isProcessing = false
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        viewModel.lastAcceptable = viewModel.isAcceptable
        val cacheDir = requireContext().cacheDir
        val fileName = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val outputFile = File(cacheDir, "$fileName.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile)
            .build()

        binding.viewFinder.post(animationTask)

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

    private fun updateStatusColor() {
        viewModel.orientation = getOrientation()
        if (viewModel.isAcceptable) {
            binding.detectionStatusView.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.green)
            )
        } else {
            binding.detectionStatusView.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.red)
            )
        }
    }

    private fun getOrientation(): Int {
        val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return when (windowManager?.defaultDisplay?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> -90
            else -> 0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, rotationResult)
            // val alpha = (-rotationResult[0]).toDouble()
            val beta = -rotationResult[1]
            // val gamma = rotationResult[2].toDouble()
            viewModel.rotation = beta
            binding.rotationTextView.text = String.format("Rotation: %f", beta)
            updateStatusColor()
        } else if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            accelLast = accelCurrent
            accelCurrent = sqrt(x * x + y * y + z * z)
            val delta = accelCurrent - accelLast
            accel = accel * 0.9f + delta
            viewModel.acceleration = accel
            binding.accelerationTextView.text = String.format("Acceleration: %f", accel)
            updateStatusColor()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
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
        private val boundingColors = arrayOf(
            Color.parseColor("#ff4500"),
            Color.parseColor("#7eda3b"),
            Color.parseColor("#ffff00"),
            Color.parseColor("#990099"),
            Color.parseColor("#ff7f50")
        )
    }
}
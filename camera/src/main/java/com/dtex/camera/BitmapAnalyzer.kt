package com.dtex.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

typealias BitmapListener = (bitmap: Bitmap) -> Unit

class BitmapAnalyzer(private val listener: BitmapListener? = null) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        image.use { bitmap.copyPixelsFromBuffer(image.planes[0].buffer) }
        listener?.let { it(bitmap) }
        image.close()
    }
}
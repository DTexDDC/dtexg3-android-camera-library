package com.dtex.camera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.dtex.camera.extensions.parcelable

open class DtexCamera {

    companion object {
        internal const val ARG_MODEL_FILE_NAME = "model_file_name"
        internal const val ARG_PHOTO_URI = "photo_uri"

        @JvmStatic
        fun with(activity: Activity): Builder {
            return Builder(activity)
        }

        @JvmStatic
        fun with(fragment: Fragment): Builder {
            return Builder(fragment)
        }

        @JvmStatic
        fun getPhotoUri(data: Intent?): Uri? {
            return data?.parcelable(ARG_PHOTO_URI)
        }
    }

    class Builder(private val activity: Activity) {

        private var fragment: Fragment? = null

        private var assetFileName: String? = null

        constructor(fragment: Fragment) : this(fragment.requireActivity()) {
            this.fragment = fragment
        }

        fun modelFile(fileName: String): Builder {
            this.assetFileName = fileName
            return this
        }

        fun createIntent(onResult: (Intent) -> Unit) {
            val intent = Intent(activity, CameraActivity::class.java)
            intent.putExtras(getBundle())
            onResult(intent)
        }

        private fun getBundle(): Bundle {
            return Bundle().apply {
                putString(ARG_MODEL_FILE_NAME, assetFileName)
            }
        }
    }
}
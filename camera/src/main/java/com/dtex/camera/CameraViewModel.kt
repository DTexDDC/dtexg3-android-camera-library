package com.dtex.camera

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    var photoUri: Uri? = null
    val isDetected: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }
}
# dtexg3-android-camera-library

Camera library for Dtex

# Installation

Step 1. Add the JitPack repository to your build file

<b>Groovy</b>

````
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven { url 'https://jitpack.io' }
    }
}
````

<b>Kotlin</b>

````
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven ("https://jitpack.io")
    }
}
````

Step 2. Add the dependency

<b>Groovy</b>

````
dependencies {
    ...
    implementation 'com.github.DTexDDC:dtexg3-android-camera-library:0.0.3'
}
````

<b>Kotlin</b>

````
dependencies {
    ...
    implementation ("com.github.DTexDDC:dtexg3-android-camera-library:0.0.3")
}
````

# Usage

Step 1. Copy the tflite model file to `assets` folder

Step 2. Register a callback for an activity result

````
private val cameraLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val res = DtexCamera.getResult(data)
            val photoUri: Uri? = res?.photoUri
            // TODO: Handle photo uri
        }
    }
````

Step 3. Launch camera activity

````
DtexCamera.with(this)
    .modelFile("modelfilename.tflite")
    .createIntent { intent ->
        cameraLauncher.launch(intent)
    }
````

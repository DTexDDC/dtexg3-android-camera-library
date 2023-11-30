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
    implementation 'com.github.vinchamp77:demo-simple-android-lib:0.0.1'
}
````

<b>Kotlin</b>
````
dependencies {
    ...
    implementation ("com.github.vinchamp77:demo-simple-android-lib:0.0.1")
}
````

# Usage
Step 1. Register a callback for an activity result
````
private val cameraLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val photoUri: Uri? = data?.getParcelableExtra("photoUri")
            // TODO: Handle photo uri
        }
    }
````

Step 2. Launch camera activity
````
val intent = Intent(this, CameraActivity::class.java)
cameraLauncher.launch(intent)
````

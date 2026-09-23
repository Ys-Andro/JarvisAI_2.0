plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

val versionPropsFile = file("${rootDir}/version.properties")
val versionProps = java.util.Properties().apply {
  if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { load(it) }
  }
}

val currentVersionCode = (System.getenv("VERSION_CODE")
  ?: project.findProperty("versionCode") as? String
  ?: versionProps.getProperty("versionCode")
  ?: "35").toInt()

val currentVersionName = System.getenv("VERSION_NAME")
  ?: project.findProperty("versionName") as? String
  ?: versionProps.getProperty("versionName")
  ?: "2.0.35"

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.jarvisai.kxvqnm"
    minSdk = 26
    targetSdk = 35
    versionCode = currentVersionCode
    versionName = currentVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters.clear()
      abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
    }
  }

  sourceSets {
    getByName("main") {
      jniLibs.directories.add("src/main/jniLibs")
    }
  }

  secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
        ?: System.getenv("KEYSTORE_PATH")
      val keystorePassword = System.getenv("RELEASE_STORE_PASSWORD")
        ?: System.getenv("STORE_PASSWORD")
      val keyAliasName = System.getenv("RELEASE_KEY_ALIAS")
        ?: System.getenv("KEY_ALIAS")
      val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
        ?: System.getenv("KEY_PASSWORD")

      val releaseFile = if (!keystorePath.isNullOrBlank()) file(keystorePath) else file("${rootDir}/release.keystore")
      val customUploadFile = file("${rootDir}/my-upload-key.jks")
      val debugStore = file("${rootDir}/debug.keystore")

      if (releaseFile.exists()) {
        storeFile = releaseFile
        storePassword = keystorePassword ?: "android"
        keyAlias = keyAliasName ?: "jarvis"
        keyPassword = keyPass ?: keystorePassword ?: "android"
      } else if (customUploadFile.exists()) {
        storeFile = customUploadFile
        storePassword = keystorePassword ?: "android"
        keyAlias = keyAliasName ?: "upload"
        keyPassword = keyPass ?: keystorePassword ?: "android"
      } else if (debugStore.exists()) {
        storeFile = debugStore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      val debugStore = file("${rootDir}/debug.keystore")
      if (debugStore.exists()) {
        storeFile = debugStore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))

  // Jetpack Compose & Material 3
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)

  // AndroidX Core & Lifecycle
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Local Storage (DataStore & Room)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Image Loading
  implementation(libs.coil.compose)

  // Testing
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

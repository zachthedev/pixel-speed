plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.android.junit5)
  alias(libs.plugins.ktfmt)
}

android {
  namespace = "tools.zach.app.pixelspeed.core"
  compileSdk { version = release(37) }

  defaultConfig { minSdk = 31 }

  // Log calls inside core reach android.util.Log on the JVM; a no-op is the right stub for them.
  testOptions { unitTests { isReturnDefaultValues = true } }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  buildFeatures {
    aidl = false
    buildConfig = false
    shaders = false
  }

  lint {
    warningsAsErrors = true
    abortOnError = true
  }
}

kotlin { jvmToolchain(25) }

ktfmt { googleStyle() }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.datastore)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlinx.coroutines.test)
  testRuntimeOnly(libs.junit.platform.launcher)
}

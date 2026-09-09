import com.android.compose.screenshot.tasks.PreviewScreenshotValidationTask
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.android.junit5)
  alias(libs.plugins.compose.screenshot)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.app.versioning)
  alias(libs.plugins.aboutlibraries)
  alias(libs.plugins.play.publisher)
}

android {
  namespace = "tools.zach.app.pixelspeed"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "tools.zach.app.pixelspeed"
    minSdk = 31
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing comes from a keystore.properties file that never enters the repository. Builds
  // without it produce an unsigned release APK, which is what F-Droid's own signing needs.
  val keystoreProperties = rootProject.file("keystore.properties")
  if (keystoreProperties.exists()) {
    val props = Properties().apply { keystoreProperties.inputStream().use { load(it) } }
    signingConfigs {
      create("release") {
        storeFile = rootProject.file(props.getProperty("storeFile"))
        storePassword = props.getProperty("storePassword")
        keyAlias = props.getProperty("keyAlias")
        keyPassword = props.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      // PNG crunching and embedded VCS metadata both make one source produce different bytes on
      // different machines.
      isCrunchPngs = false
      vcsInfo { include = false }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Without the release keystore the APK still has to install on an emulator, so it falls back
      // to the debug key. F-Droid strips signatures before comparing builds. The release workflow
      // sets REQUIRE_RELEASE_SIGNING so a missing keystore fails the build there instead.
      signingConfig =
        signingConfigs.findByName("release")
          ?: if (System.getenv("REQUIRE_RELEASE_SIGNING") == "true") {
            error("keystore.properties is missing and REQUIRE_RELEASE_SIGNING is set")
          } else {
            signingConfigs.getByName("debug")
          }
    }
  }

  // Play's dependency metadata block is encrypted with a Google key, so nobody else can reproduce
  // it.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  // The per-app language picker in system settings lists exactly the locales that have resources.
  androidResources { generateLocaleConfig = true }

  testOptions { unitTests { isIncludeAndroidResources = true } }

  // Compose Preview Screenshot Testing is still gated behind this module-level flag.
  experimentalProperties["android.experimental.enableScreenshotTest"] = true

  lint {
    warningsAsErrors = true
    abortOnError = true
  }
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    optIn.addAll(
      "androidx.compose.material3.ExperimentalMaterial3Api",
      "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
      "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
    )
  }
}

ktfmt { googleStyle() }

// The threshold is a fraction of the image's pixels, and one value covers a 474 by 137 pill and a
// full 1281 by 2856 screen alike: at this value a screen tolerates a 19 pixel square, about 6 dp,
// which absorbs anti-aliasing drift from a renderer bump and still fails on a moved element.
tasks.withType<PreviewScreenshotValidationTask>().configureEach {
  testEngineInput.threshold.set(0.0001f)
}

// The git tag is the only place a version is written. Release tags look like v1.2.3; versionName
// drops the prefix and versionCode is 1.2.3 -> 10203.
appVersioning {
  // A git worktree's .git is a file naming its directory under the main checkout's .git, and the
  // plugin only reads a repository root it is handed.
  val dotGit: File = rootProject.file(".git")
  if (dotGit.isFile) {
    val worktreeGitDir = File(dotGit.readText().removePrefix("gitdir:").trim())
    val worktrees: File? = worktreeGitDir.parentFile
    val mainGitDir: File? = worktrees?.parentFile
    if (worktrees?.name == "worktrees" && mainGitDir?.parentFile != null) {
      gitRootDirectory.set(mainGitDir.parentFile)
    }
  }
  overrideVersionName { gitTag, _, _ -> gitTag.rawTagName.removePrefix("v") }
  overrideVersionCode { gitTag, _, _ ->
    gitTag.rawTagName.removePrefix("v").split('.').map(String::toInt).let { (major, minor, patch) ->
      major * 10_000 + minor * 100 + patch
    }
  }
}

// Play uploads run only where a service account key exists: the release workflow writes the key
// to a temporary file and names it in PLAY_SERVICE_ACCOUNT_FILE. Without that the publish tasks
// are disabled and the plugin adds nothing to a build.
play {
  val credentials: String? = System.getenv("PLAY_SERVICE_ACCOUNT_FILE")
  enabled.set(credentials != null)
  credentials?.let { serviceAccountCredentials.set(file(it)) }
  track.set("internal")
  defaultToAppBundles.set(true)
}

// The plugin writes the license list into every variant as a generated raw resource. Offline mode
// keeps the build off the network: license texts come from the plugin, never from a fetch.
aboutLibraries {
  offlineMode = true
  export { excludeFields.addAll("funding") }
}

/**
 * Copies root policy and release documents into a variant's assets, so the About screens read the
 * same files Play and GitHub link, with no second copy to drift. Before the first release there is
 * nothing to copy and the screens that read a missing asset say so.
 */
abstract class CopyDocsTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val docs: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun copy() {
    val target: File = outputDirectory.get().asFile
    if (target.exists()) check(target.deleteRecursively()) { "Cannot clear $target" }
    check(target.mkdirs()) { "Cannot create $target" }
    docs.files.filter { it.isFile }.forEach { it.copyTo(File(target, it.name), overwrite = true) }
  }
}

// The privacy policy in every language, PRIVACY.<language>[-<region>].md, and nothing else.
val POLICY_FILE: Regex = Regex("""PRIVACY(\.[a-z]{2,3}(-[A-Za-z0-9]{2,8})?)?\.md""")

androidComponents {
  onVariants { variant ->
    val copyDocs: TaskProvider<CopyDocsTask> =
      tasks.register<CopyDocsTask>("copy${variant.name.replaceFirstChar { it.uppercase() }}Docs") {
        docs.from(rootProject.file("CHANGELOG.md"))
        docs.from(
          rootProject.projectDir
            .listFiles { file -> file.isFile && POLICY_FILE.matches(file.name) }
            .orEmpty()
            .toList()
        )
      }
    variant.sources.assets?.addGeneratedSourceDirectory(copyDocs, CopyDocsTask::outputDirectory)
  }
}

dependencies {
  implementation(project(":core"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // AndroidX core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.service)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.material3.adaptive)
  implementation(libs.androidx.compose.material3.adaptive.layout)
  implementation(libs.androidx.compose.material3.adaptive.navigation3)
  implementation(libs.androidx.graphics.shapes)
  debugImplementation(libs.androidx.compose.ui.tooling)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling)
  screenshotTestImplementation(libs.screenshot.validation.api)

  // Navigation and DI
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
  ksp(libs.hilt.compiler)

  // Storage
  implementation(libs.androidx.datastore)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  // Settings UI
  implementation(libs.compose.colorpicker)
  implementation(libs.aboutlibraries.compose.m3)

  // JVM tests: JUnit Jupiter for logic, the vintage engine for Robolectric and Compose suites.
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit4)
  testImplementation(libs.robolectric)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.test.core)
  testRuntimeOnly(libs.junit.platform.launcher)
  testRuntimeOnly(libs.junit.vintage.engine)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

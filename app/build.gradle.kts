plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// `google-services.json` is deliberately NOT in the repository (see .gitignore),
// and `processDebugGoogleServices` fails without it, so the Firebase plugins are
// applied only when the config is present. A contributor without the file gets a
// working app with crash reporting inert, which is safe by construction:
// `FirebaseCrashlyticsReporter` resolves the SDK handle lazily per call, and
// `CrashReporting.applyAtStartup` / `setEnabled` catch the
// `IllegalStateException` that `FirebaseCrashlytics.getInstance()` throws when no
// default `FirebaseApp` exists.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
} else {
    logger.lifecycle(
        "FastMask: app/google-services.json not found — building without Firebase " +
            "Crashlytics. The app runs normally; crash reporting is inert. See README " +
            "§ Build from Source."
    )
}

// Play licence key and release keystore come from the environment first, then
// from a Gradle property (e.g. ~/.gradle/gradle.properties), and are never in
// the repository. Optional values may be absent (a debug build needs none of
// them); `required = true` fails the build when neither source has the value.
fun envOrProperty(env: String, property: String, required: Boolean = false): String? =
    System.getenv(env)
        ?: if (required) project.property(property) as String
        else project.findProperty(property) as String?

val playLicenseKey: String? = envOrProperty("FASTMASK_PLAY_LICENSE_KEY", "fastmask.playLicenseKey")
val releaseKeystorePath: String? = envOrProperty("FASTMASK_KEYSTORE", "fastmask.keystore")
val hasReleaseKeystore: Boolean = releaseKeystorePath != null

android {
    namespace = "com.fastmask"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fastmask"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "1.14.1"

        // Hilt needs its own Application in instrumented tests; HiltTestRunner
        // swaps FastMaskApplication for HiltTestApplication.
        testInstrumentationRunner = "com.fastmask.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Monetization kill-switch. Set to "false" to ship a build with every
        // Pro entry point hidden (existing Pro owners keep their entitlement).
        // See Plans/monetization.md § Rollback.
        buildConfigField("boolean", "MONETIZATION_ENABLED", "true")

        // Base64 RSA public key from Play Console (Monetization setup →
        // Licensing), used to verify purchase signatures (see PurchaseSecurity).
        // Provided out-of-source via the `fastmask.playLicenseKey` Gradle
        // property (e.g. ~/.gradle/gradle.properties) or FASTMASK_PLAY_LICENSE_KEY
        // env. Empty by default so dev/CI builds work; a release build MUST set
        // it, otherwise purchase signatures are not verified.
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"${playLicenseKey.orEmpty()}\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = envOrProperty("FASTMASK_STORE_PWD", "fastmask.storePassword", required = true)
                keyAlias = envOrProperty("FASTMASK_KEY_ALIAS", "fastmask.keyAlias", required = true)
                keyPassword = envOrProperty("FASTMASK_KEY_PWD", "fastmask.keyPassword", required = true)
            }
        }
    }

    buildTypes {
        debug {
            // Nothing to symbolicate without R8, and a debug build must not
            // reach Crashlytics' upload endpoint on every assemble. Runtime
            // collection is disabled separately and unconditionally by
            // CrashReporting, whatever the user preference says.
            //
            // Guarded on the same flag as the plugin itself: `configure<T>` looks
            // the extension up by type and throws UnknownDomainObjectException
            // when the Crashlytics plugin was not applied.
            if (hasFirebaseConfig) {
                configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                    mappingFileUploadEnabled = false
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// A SIGNED release with no licence key silently ships with purchase signature
// verification disabled (PlayBillingDataSource.isSignatureValid returns true
// when the key is blank), so a hooked billing service unlocks Pro for free.
// Unsigned/CI builds stay permissive, so `assembleRelease` without a keystore
// keeps working as a smoke test.
//
// Checked on the task graph rather than in a doFirst: the graph is resolved
// before ANY task runs, so a misconfigured release fails in a second instead of
// after a full R8 build — and it fails ahead of validateSigningRelease, which
// would otherwise mask this error behind its own.
gradle.taskGraph.whenReady {
    val releasing = allTasks.any {
        it.project == project && (it.name == "assembleRelease" || it.name == "bundleRelease")
    }
    if (!releasing) return@whenReady

    if (hasReleaseKeystore && playLicenseKey.isNullOrBlank()) {
        throw GradleException(
            "Refusing to build a signed release without a Play licence key: purchase " +
                "signatures would not be verified, so any forged purchase would unlock " +
                "Pro. Set FASTMASK_PLAY_LICENSE_KEY or the fastmask.playLicenseKey Gradle " +
                "property (Play Console → Monetization setup → Licensing)."
        )
    }
}

// FontSubsetTest reads the bundled font by path; declare it so Gradle reruns the test when it changes.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/res/font/google_sans_flex.ttf")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose. Versions, including the material3 pin above the BOM, live in
    // gradle/libs.versions.toml.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    // Large screen support
    implementation(libs.androidx.compose.material3.adaptive)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // Google Play Billing (base Java artifact, see the catalog entry)
    implementation(libs.play.billing)

    // Biometric app lock (Pro feature)
    implementation(libs.androidx.biometric)

    // Google Play In-App Updates. Never gated on Pro, collects nothing: the
    // dismissed version code lives on the device and goes nowhere.
    implementation(libs.play.app.update)

    // Security for encrypted storage
    implementation(libs.androidx.security.crypto)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase Crashlytics: crash diagnostics only. Google Analytics is
    // deliberately NOT on the graph: this app reports that it crashed, never
    // what its user did.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing. Instrumented tests are test-only dependencies, so nothing ships
    // to users; the AndroidX Test pins are explained in the catalog.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Instrumented tests exercise the real Hilt graph, so they need the test
    // Application and the generated test components.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

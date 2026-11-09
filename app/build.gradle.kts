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
// working app with crash reporting inert.
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

// Play licence key and release keystore are never in the repository.
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

        // Hilt needs its own Application in instrumented tests.
        testInstrumentationRunner = "com.fastmask.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Monetization kill-switch. Set to "false" to ship a build with every
        // Pro entry point hidden (existing Pro owners keep their entitlement).
        buildConfigField("boolean", "MONETIZATION_ENABLED", "true")

        // Base64 RSA public key from Play Console (Monetization setup →
        // Licensing), used to verify purchase signatures. Provided out-of-source
        // via the `fastmask.playLicenseKey` Gradle property or
        // FASTMASK_PLAY_LICENSE_KEY env. Empty by default so dev/CI builds work.
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
            // reach Crashlytics' upload endpoint on every assemble.
            //
            // `configure<T>` looks the extension up by type and throws
            // UnknownDomainObjectException when the Crashlytics plugin was not applied.
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

// Declared so Gradle reruns the tests when the bundled font changes.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/res/font/google_sans_flex.ttf")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    implementation(libs.androidx.compose.material3.adaptive)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    implementation(libs.play.billing)

    implementation(libs.androidx.biometric)

    // Google Play In-App Updates. Never gated on Pro, collects nothing: the
    // dismissed version code lives on the device and goes nowhere.
    implementation(libs.play.app.update)

    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.datastore.preferences)

    // Firebase Crashlytics: crash diagnostics only. Google Analytics is
    // deliberately NOT on the graph: this app reports that it crashed, never
    // what its user did.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.kotlinx.coroutines.android)

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

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt")
}

// `google-services.json` is deliberately NOT in the repository (see .gitignore),
// so applying the Firebase plugins unconditionally made a clean clone unbuildable:
// `processDebugGoogleServices` fails with "File google-services.json is missing"
// before a line of Kotlin is compiled — including for the exact `./gradlew
// assembleDebug` the README, CLAUDE.md and AGENTS.md tell contributors to run.
//
// Applying them only when the config is present keeps the maintainer's builds
// (and every release) fully instrumented, while a contributor without the file
// gets a working app with crash reporting inert. That degradation is safe by
// construction, not by luck: `FirebaseCrashlyticsReporter` resolves the SDK
// handle lazily per call, and `CrashReportingStartup` already catches the
// `IllegalStateException` that `FirebaseCrashlytics.getInstance()` throws when
// no default `FirebaseApp` exists — the path OEM ROMs without content providers
// already take. `SettingsViewModel.onCrashReportingToggled` wraps its call in
// `runCatching` for the same reason.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle(
        "FastMask: app/google-services.json not found — building without Firebase " +
            "Crashlytics. The app runs normally; crash reporting is inert. See README " +
            "§ Build from Source."
    )
}

android {
    namespace = "com.fastmask"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fastmask"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "1.10.1"

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
        val playLicenseKey = System.getenv("FASTMASK_PLAY_LICENSE_KEY")
            ?: (project.findProperty("fastmask.playLicenseKey") as String?)
            ?: ""
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("FASTMASK_KEYSTORE")
                ?: (project.findProperty("fastmask.keystore") as String?)
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FASTMASK_STORE_PWD")
                    ?: project.property("fastmask.storePassword") as String
                keyAlias = System.getenv("FASTMASK_KEY_ALIAS")
                    ?: project.property("fastmask.keyAlias") as String
                keyPassword = System.getenv("FASTMASK_KEY_PWD")
                    ?: project.property("fastmask.keyPassword") as String
            }
        }
    }

    buildTypes {
        debug {
            // Nothing to symbolicate without R8, and a debug build must not
            // reach Crashlytics' upload endpoint on every assemble. Runtime
            // collection is disabled separately and unconditionally by
            // CrashReportingPolicy, whatever the user preference says.
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
            val hasReleaseKeystore = System.getenv("FASTMASK_KEYSTORE") != null
                || project.hasProperty("fastmask.keystore")
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

    kotlinOptions {
        jvmTarget = "17"
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

    val signed = System.getenv("FASTMASK_KEYSTORE") != null
        || project.hasProperty("fastmask.keystore")
    val key = System.getenv("FASTMASK_PLAY_LICENSE_KEY")
        ?: (project.findProperty("fastmask.playLicenseKey") as String?)

    if (signed && key.isNullOrBlank()) {
        throw GradleException(
            "Refusing to build a signed release without a Play licence key: purchase " +
                "signatures would not be verified, so any forged purchase would unlock " +
                "Pro. Set FASTMASK_PLAY_LICENSE_KEY or the fastmask.playLicenseKey Gradle " +
                "property (Play Console → Monetization setup → Licensing)."
        )
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Large screen support
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.58")
    kapt("com.google.dagger:hilt-compiler:2.58")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Google Play Billing — base (Java) artifact, NOT billing-ktx: the ktx
    // extensions are compiled with a newer Kotlin than this project's 1.9.22
    // and only wrap listeners we replace with our own suspend wrappers anyway.
    // 8.x satisfies Play's "Billing Library 8+" requirement (Aug 31, 2026).
    implementation("com.android.billingclient:billing:8.3.0")

    // Biometric app lock (Pro feature)
    implementation("androidx.biometric:biometric:1.1.0")

    // Security for encrypted storage
    // 1.1.0 stable (same API line as the previously pinned 1.1.0-alpha06 —
    // TokenStorage's MasterKey.Builder API is unchanged). Closes the alpha-pin
    // trade-off documented in Plans/security-audit-report.md F-05.
    implementation("androidx.security:security-crypto:1.1.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Firebase Crashlytics — crash diagnostics only. Google Analytics is
    // deliberately NOT on the graph: this app reports that it crashed, never
    // what its user did. The BOM pins the SDK versions as a matched set.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    // AndroidX Test 1.6.x / Espresso 3.6.x: the 3.5.1 line calls
    // InputManager.getInstance(), removed in Android 16 (API 36) — every
    // instrumented test died in Espresso.onIdle before reaching an assertion.
    // Test-only dependencies, so nothing ships to users.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Instrumented tests exercise the real Hilt graph, so they need the test
    // Application and the generated test components.
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.58")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.58")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

kotlin {
    jvmToolchain(17)
}

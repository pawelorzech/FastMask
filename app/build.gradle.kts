plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt")
}

android {
    namespace = "com.fastmask"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fastmask"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Monetization kill-switch. Set to "false" to ship a build with every
        // Pro entry point hidden (existing Pro owners keep their entitlement).
        // See Plans/monetization.md § Rollback.
        buildConfigField("boolean", "MONETIZATION_ENABLED", "true")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

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
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
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
    // Pinned to 1.1.0-alpha06 because TokenStorage uses MasterKey.Builder API,
    // which only exists in the 1.1.x line. Migrating to 1.0.0 requires rewriting
    // TokenStorage to the deprecated MasterKeys API — tracked separately.
    // See Plans/security-audit-report.md F-05.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

kotlin {
    jvmToolchain(17)
}

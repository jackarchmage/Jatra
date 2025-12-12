plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.jks.jatrav3"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jks.jatrav3"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    // handle duplicate native libs if necessary (last-resort)
    packagingOptions {
        jniLibs {
            pickFirst("lib/arm64-v8a/libfilament-jni.so")
            pickFirst("lib/armeabi-v7a/libfilament-jni.so")
            pickFirst("lib/x86/libfilament-jni.so")
            pickFirst("lib/x86_64/libfilament-jni.so")
        }
    }
}

// Force and exclude resolution strategy (safety net)
configurations.all {
    // remove older com.google.ar.sceneform and gorisse packages if present transitively
    exclude(group = "com.google.ar.sceneform")
    exclude(group = "com.gorisse.thomas.sceneform")

    resolutionStrategy {
        // Force Filament to the newer version used by SceneView
        force("com.google.android.filament:filament-android:1.56.0")
        force("com.google.android.filament:gltfio-android:1.56.0")
        force("com.google.android.filament:filament-utils-android:1.56.0")
    }
}

dependencies {
    // --- Android + Jetpack deps (keep yours) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material3)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.core)
    implementation(libs.litert)

    // keep lifecycle viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.github.sceneview:sceneview:2.3.0")
    implementation("io.github.sceneview:arsceneview:2.3.0")
    implementation("com.google.ar:core:1.50.0")

    // --- networking + other libs (keep yours) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("com.razorpay:checkout:1.6.41")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Media3 ExoPlayer core
    implementation("androidx.media3:media3-exoplayer:1.3.1")
// Media3 UI components (StyledPlayerView replaces PlayerView)
    implementation("androidx.media3:media3-ui:1.3.1")

// use latest if available

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


}

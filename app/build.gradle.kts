plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val forceUpdate: Boolean by project
val minSupportedVersionCode: Int by project

android {
    namespace = "com.jons.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jons.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

tasks.register("printVersionInfo") {
    doLast {
        println("VERSION_CODE=${android.defaultConfig.versionCode}")
        println("VERSION_NAME=${android.defaultConfig.versionName}")
        println("FORCE_UPDATE=$forceUpdate")
        println("MIN_SUPPORTED_VERSION_CODE=$minSupportedVersionCode")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

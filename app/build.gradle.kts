plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.airdvr.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.airdvr.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BASE_URL", "\"https://api.airdvr.com/\"")
    }

    flavorDimensions += "platform"

    productFlavors {
        create("googletv") {
            dimension = "platform"
            applicationIdSuffix = ".googletv"
            versionNameSuffix = "-googletv"
            buildConfigField("String", "PLATFORM", "\"googletv\"")
        }
        create("firetv") {
            dimension = "platform"
            applicationIdSuffix = ".firetv"
            versionNameSuffix = "-firetv"
            buildConfigField("String", "PLATFORM", "\"firetv\"")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("Boolean", "DEBUG_LOGGING", "true")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DEBUG_LOGGING", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Gson
    implementation(libs.gson)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Security
    implementation(libs.androidx.security.crypto)
}

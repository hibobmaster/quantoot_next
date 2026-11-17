plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.quanquan.quantoot"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.quanquan.quantoot"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.6.1"
        testInstrumentationRunner = "com.quanquan.quantoot.StorageInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    androidTestImplementation(libs.coroutines.core)
}

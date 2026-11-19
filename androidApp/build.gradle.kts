import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val abiVersionOffsets = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

android {
    namespace = "com.quanquan.quantoot"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.quanquan.quantoot"
        minSdk = 26
        targetSdk = 37
        // Reserve the last digit for per-ABI release APKs.
        versionCode = 2019 * 10
        versionName = "0.6.7"
        testInstrumentationRunner = "com.quanquan.quantoot.StorageInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    splits {
        abi {
            isEnable = providers.gradleProperty("splitApks").orNull == "true"
            reset()
            include(*abiVersionOffsets.keys.toTypedArray())
            isUniversalApk = false
        }
    }
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

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.filters.firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }?.let { abi ->
                output.versionCode.set(output.versionCode.get() + abiVersionOffsets.getValue(abi.identifier))
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    androidTestImplementation(libs.coroutines.core)
}

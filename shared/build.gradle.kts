import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp)
}

kotlin {
    android {
        namespace = "com.quanquan.quantoot.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "QuanTootShared"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.icons)
            implementation(libs.compose.backhandler)
            implementation(libs.qrose)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.core)
            implementation(libs.ktor.websockets)
            implementation(libs.coil.compose)
            implementation(libs.coil.network)
        }
        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
            implementation(libs.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.mock)
        }
    }
}

compose.resources {
    packageOfResClass = "com.quanquan.quantoot.resources"
    publicResClass = true
}

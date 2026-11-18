buildscript {
    dependencies {
        constraints {
            // Security updates for libraries on the Android Gradle plugin classpath.
            add("classpath", "org.jdom:jdom2:2.0.6.1")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6")
            add("classpath", "org.apache.commons:commons-lang3:3.20.0")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.85")
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.85")
            add("classpath", "org.bouncycastle:bcutil-jdk18on:1.85")
            add("classpath", "com.google.guava:guava:33.7.2-jre")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp) apply false
}

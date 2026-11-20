pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            if (System.getenv("CI_XCODE_CLOUD") == "TRUE") {
                // Use Google's alternate download host on Xcode Cloud workers.
                url = uri("https://dl-ssl.google.com/android/maven2/")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "QuanToot"

include(":shared", ":androidApp")

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
                // Use Google's download CDN for Maven artifacts on Xcode Cloud workers.
                url = uri("https://redirector.gvt1.com/edgedl/android/maven2/")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "QuanToot"

include(":shared", ":androidApp")

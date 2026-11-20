pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        if (System.getenv("CI_XCODE_CLOUD") == "TRUE") {
            exclusiveContent {
                forRepository {
                    maven {
                        name = "VerifiedXcodeCloudAndroidX"
                        url = uri(".gradle/xcode-cloud-google-maven")
                    }
                }
                filter {
                    includeGroupByRegex("androidx[.].+")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "QuanToot"

include(":shared", ":androidApp")

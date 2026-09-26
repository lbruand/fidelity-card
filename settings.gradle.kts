pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// Lets Gradle auto-download a matching JDK toolchain (e.g. 17) when the
// machine running the build doesn't already have one installed, instead of
// failing with "Toolchain download repositories have not been configured."
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fidelity-card"

include(":crypto")
include(":core")
include(":app")

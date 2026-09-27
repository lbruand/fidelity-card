plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.fidelitycard"
version = "0.1.0"

kotlin {
    // Must not exceed :app's own toolchain (17): :app depends on this
    // module directly, and a JVM can't load class files compiled for a
    // newer bytecode version than it targets.
    jvmToolchain(17)
}

dependencies {
    // Only for WireWriter/WireReader (generic binary framing, not the
    // stamp-protocol message types) - the backup format is a distinct
    // concern from the QR wire format, deliberately not layered into
    // :crypto's own message set.
    implementation(project(":crypto"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

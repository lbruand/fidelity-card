plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.fidelitycard"
version = "0.1.0"

dependencies {
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

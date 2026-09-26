plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.fidelitycard"
version = "0.1.0"

dependencies {
    // Deliberately no dependency on :crypto: everything here operates on
    // plain serials/counts (already extracted from a verified StampToken
    // etc. by the caller), so these business rules stay reusable wherever
    // a card's stamp/redemption bookkeeping needs checking - independent of
    // the wire format or even of a card being backed by crypto at all.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    // Sirius Web is consumed as a binary dependency only; its source is never modified (spec §2.3).
    implementation(libs.sirius.web.starter)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Jar>("jar") {
    enabled = false // only the executable bootJar is shipped
}

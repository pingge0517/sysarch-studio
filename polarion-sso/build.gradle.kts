plugins {
    java
}

// Polarion single sign-on extension (embedding SysArch in Polarion). Installed into Polarion's extensions folder, it
// runs in Polarion's Tomcat 9 (javax.servlet, Java 17 like the other extensions there), behind Polarion's login, and
// signs who is logged in for SysArch's /api/v1/auth/polarion-sso. Plain JDK + servlet API: nothing else goes into
// Polarion. `./gradlew :polarion-sso:polarionExtension` builds build/extension/ and build/distributions/*.zip.
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get())) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    compileOnly("javax.servlet:javax.servlet-api:4.0.1") // provided by Polarion's Tomcat
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation("javax.servlet:javax.servlet-api:4.0.1")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val pluginDir = "com.example.sysarch.sso/eclipse/plugins/com.example.sysarch.sso_1.0.0"

tasks.jar {
    archiveFileName.set("sysarch-sso.jar")
}

val polarionExtensionDir = tasks.register<Sync>("polarionExtensionDir") {
    group = "build"
    description = "Lays out the Polarion extension folder (copy build/extension/com.example.sysarch.sso into <polarion>/polarion/extensions)."
    into(layout.buildDirectory.dir("extension"))
    into(pluginDir) {
        from("src/main/extension")
        from(tasks.jar)
    }
}

tasks.register<Zip>("polarionExtension") {
    group = "build"
    description = "The Polarion extension as a zip (unzip into <polarion>/polarion/extensions)."
    dependsOn(polarionExtensionDir)
    from(layout.buildDirectory.dir("extension"))
    archiveFileName.set("com.example.sysarch.sso.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named("assemble") {
    dependsOn("polarionExtension")
}

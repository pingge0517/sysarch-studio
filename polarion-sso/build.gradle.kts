plugins {
    java
}

// Polarion single sign-on extension (embedding SysArch in Polarion). Installed into Polarion's extensions folder, it
// runs in Polarion's Tomcat 9 (javax.servlet, Java 17 like the other extensions there), behind Polarion's login, and
// signs who is logged in for SysArch's /api/v1/auth/polarion-sso. Plain JDK + servlet API: nothing else goes into
// Polarion. `./gradlew :polarion-sso:polarionExtension` builds build/extension/ and build/distributions/*.zip.
//
// The Work Item form panel (source set `polarion`: SysArchFormExtension, SysArchFormModule) implements Polarion's own
// interfaces, so it compiles only against a Polarion installation: POLARION_HOME (or -Ppolarion.home), else
// C:/Polarion/polarion. Without one (CI) the extension is built without the panel, and its manifest has no
// Guice-Modules header.
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get())) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

val polarionHome = file(
    (findProperty("polarion.home") as String?) ?: System.getenv("POLARION_HOME") ?: "C:/Polarion/polarion"
)
val polarionPlugins = polarionHome.resolve("plugins")
val withFormPanel = polarionPlugins.resolve("com.polarion.alm.ui_3.25.12").isDirectory
        || (polarionPlugins.isDirectory && polarionPlugins.listFiles { f -> f.name.startsWith("com.polarion.alm.ui_") }?.isNotEmpty() == true)

val polarion: SourceSet = sourceSets.create("polarion")

dependencies {
    compileOnly("javax.servlet:javax.servlet-api:4.0.1") // provided by Polarion's Tomcat
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation("javax.servlet:javax.servlet-api:4.0.1")
    testRuntimeOnly(libs.junit.platform.launcher)
    "polarionCompileOnly"(sourceSets.main.get().output)
    if (withFormPanel) {
        // Polarion's own jars (compile only; Polarion provides them at runtime)
        "polarionCompileOnly"(fileTree(polarionPlugins) {
            include("com.polarion.*/**/*.jar")
        })
    }
}

tasks.named("compilePolarionJava") {
    enabled = withFormPanel
}

tasks.test {
    useJUnitPlatform()
}

val pluginDir = "com.example.sysarch.sso/eclipse/plugins/com.example.sysarch.sso_1.0.0"

tasks.jar {
    archiveFileName.set("sysarch-sso.jar")
    if (withFormPanel) {
        from(polarion.output)
    }
}

/** The bundle manifest; the form panel needs Polarion's UI, tracker and Guice bundles and registers its module. */
fun manifest(formPanel: Boolean): String = buildString {
    appendLine("Manifest-Version: 1.0")
    appendLine("Bundle-ManifestVersion: 2")
    appendLine("Bundle-Name: SysArch Studio single sign-on")
    appendLine("Bundle-SymbolicName: com.example.sysarch.sso; singleton:=true")
    appendLine("Bundle-Version: 1.0.0")
    appendLine("Bundle-RequiredExecutionEnvironment: JavaSE-17")
    appendLine("Bundle-ClassPath: sysarch-sso.jar")
    // com.polarion.platform holds DoAsFilter (web.xml): without it the web app does not start
    val bundles = mutableListOf("com.polarion.platform", "com.polarion.portal.tomcat")
    if (formPanel) {
        bundles += listOf("com.polarion.alm.ui", "com.polarion.alm.tracker", "com.polarion.platform.persistence", "com.polarion.platform.guice")
    }
    appendLine("Require-Bundle: " + bundles.joinToString(",\n "))
    appendLine("Import-Package: javax.servlet,javax.servlet.http")
    if (formPanel) {
        appendLine("Guice-Modules: com.example.sysarch.polarion.sso.SysArchFormModule")
    }
    appendLine("Eclipse-AutoStart: true")
    appendLine("Eclipse-BundleShape: dir")
}

val writeManifest = tasks.register("writeExtensionManifest") {
    val out = layout.buildDirectory.file("extension-manifest/META-INF/MANIFEST.MF")
    inputs.property("formPanel", withFormPanel)
    outputs.file(out)
    doLast {
        out.get().asFile.apply {
            parentFile.mkdirs()
            writeText(manifest(withFormPanel))
        }
    }
}

val polarionExtensionDir = tasks.register<Sync>("polarionExtensionDir") {
    group = "build"
    description = "Lays out the Polarion extension folder (copy build/extension/com.example.sysarch.sso into <polarion>/polarion/extensions)."
    dependsOn(writeManifest)
    into(layout.buildDirectory.dir("extension"))
    into(pluginDir) {
        from("src/main/extension")
        from(layout.buildDirectory.dir("extension-manifest"))
        from(tasks.jar)
    }
    doLast {
        logger.lifecycle("Polarion extension " + (if (withFormPanel) "with" else "without") + " the Work Item form panel (Polarion at $polarionHome)")
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

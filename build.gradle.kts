plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.openapi.generator) apply false
}

val javaVersion = libs.versions.java.get().toInt()

allprojects {
    group = "com.example"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

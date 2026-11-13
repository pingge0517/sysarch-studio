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

        // P4 T8 (security review F6): security patch versions above the Spring Boot BOM (libs.versions.toml,
        // "security-*"). Constraints only raise a version a module already uses; they add no dependency.
        val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun v(name: String) = catalog.findVersion(name).get().requiredVersion
        dependencies {
            "implementation"(platform(catalog.findLibrary("security-jackson-bom").get()))
            "implementation"(platform(catalog.findLibrary("security-log4j-bom").get()))
            constraints {
                listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach {
                    "implementation"("org.apache.tomcat.embed:$it:${v("security-tomcat")}") { because("security fixes (OSV-Scanner, P4 T8)") }
                }
                "implementation"("org.freemarker:freemarker:${v("security-freemarker")}") { because("security fixes (OSV-Scanner, P4 T8)") }
                "implementation"("org.postgresql:postgresql:${v("security-postgresql")}") { because("security fixes (OSV-Scanner, P4 T8)") }
            }
        }

        // P4 T8 (security review F6): the module's resolved runtime dependencies in Gradle's lockfile format, for
        // OSV-Scanner (CI job "dependency-scan"). Only written for the scan, never used for resolution (no dependency
        // locking: updating a dependency stays a one-line change). Jars from the p2/ivy repositories (Eclipse OCL,
        // pseudo-group eclipse.ocl.p2) have no Maven coordinates OSV knows and are not covered.
        tasks.register("osvLockfile") {
            group = "verification"
            description = "Writes build/osv/gradle.lockfile (resolved runtime dependencies) for OSV-Scanner."
            val runtime = project.configurations.named("runtimeClasspath")
            val out = project.layout.buildDirectory.file("osv/gradle.lockfile")
            outputs.file(out)
            outputs.upToDateWhen { false }
            doLast {
                val coordinates = runtime.get().incoming.resolutionResult.allComponents
                    .map { it.id }
                    .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
                    .map { "${it.group}:${it.module}:${it.version}=runtimeClasspath" }
                    .toSortedSet()
                val file = out.get().asFile
                file.parentFile.mkdirs()
                file.writeText(
                    "# Written by :${project.name}:osvLockfile for OSV-Scanner; not used for dependency resolution.\n" +
                        coordinates.joinToString("") { "$it\n" } + "empty=\n"
                )
                logger.lifecycle("${coordinates.size} runtime dependencies -> ${file.relativeTo(rootDir)}")
            }
        }
    }
}

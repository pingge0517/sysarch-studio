plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
}

// T7: the Polarion proxy API (spec §6.1, copied verbatim) is the contract. openapi-generator produces only the Spring
// interfaces and DTOs (no controllers); our controllers implement them. Generated into build/, never committed.
val openApiSpec = layout.projectDirectory.file("src/main/resources/api/polarion-proxy.openapi.yaml")
val openApiOutput = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(openApiSpec.asFile.path)
    outputDir.set(openApiOutput.get().asFile.path)
    apiPackage.set("com.example.sysarch.sync.api")
    modelPackage.set("com.example.sysarch.sync.api.model")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "useSpringBoot3" to "true",
        "useJakartaEe" to "true",
        "useTags" to "false",
        "openApiNullable" to "false",
        "useBeanValidation" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "dateLibrary" to "java8",
        "hideGenerationTimestamp" to "true",
    ))
}

sourceSets.main {
    java.srcDir(openApiOutput.map { it.dir("src/main/java") })
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    // Sirius Web is consumed as a binary dependency only; its source is never modified (spec §2.3).
    implementation(libs.sirius.web.starter)
    // The Sirius Web UI (projects, explorer, diagrams), so the VSM diagrams can be used in a browser on :8080.
    runtimeOnly(libs.sirius.web.frontend)
    implementation(libs.spring.boot.starter.actuator)
    // Used directly by the generated sysarch model/edit code. Same versions Sirius Web brings.
    implementation(libs.bundles.emf.runtime)
    // Eclipse OCL Pivot for constraint checks (T6): the exact verified jar set, without transitive resolution.
    // It includes org.eclipse.core.runtime and org.eclipse.osgi, which the generated
    // SysarchEditPlugin.Implementation (extends EMFPlugin.EclipsePlugin, OSGi only) also needs to compile.
    for (dependency in libs.bundles.ocl.runtime.get()) {
        implementation("${dependency.module}:${dependency.versionConstraint.requiredVersion}") { isTransitive = false }
    }

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// EMFPlugin (SysarchEditPlugin) resolves plugin.properties and icons/ relative to the compiled classes'
// location. Gradle keeps classes and resources in separate dirs by default, which breaks every edit-layer
// label in tests and bootRun (verified: MissingResourceException). The boot jar merges them anyway.
sourceSets.main {
    output.setResourcesDir(java.destinationDirectory)
}

tasks.named<Jar>("jar") {
    enabled = false // only the executable bootJar is shipped
}

// CI gate 3 (spec §5.2, T5 step 5, T9 step 3): the refresh perf report written by IbdSyncTest must exist and meet
// the spec's thresholds (500 elements, 20 inserts: median < 800 ms, P95 < 2000 ms). The thresholds are fixed here, not
// read from the report, so a test cannot loosen its own gate.
val checkPerfThreshold = tasks.register("checkPerfThreshold") {
    group = "verification"
    description = "Fails unless build/perf-refresh.json reports median < 800 ms and P95 < 2000 ms."
    val report = layout.buildDirectory.file("perf-refresh.json")
    dependsOn(tasks.named("test"))
    inputs.file(report)
    doLast {
        val file = report.get().asFile
        if (!file.isFile) {
            throw GradleException("perf gate: ${file} is missing; IbdSyncTest did not run")
        }
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
        val median = (json["medianMs"] as Number).toDouble()
        val p95 = (json["p95Ms"] as Number).toDouble()
        val elements = (json["elements"] as Number).toInt()
        val additions = (json["additions"] as Number).toInt()
        if (elements < 500 || additions < 20) {
            throw GradleException("perf gate: the scenario must be 500 elements and 20 inserts, was $elements and $additions")
        }
        if (median >= 800.0 || p95 >= 2000.0) {
            throw GradleException("perf gate failed: median $median ms (limit 800), P95 $p95 ms (limit 2000)")
        }
        logger.lifecycle("perf gate passed: median $median ms, P95 $p95 ms ($elements elements, $additions inserts)")
    }
}

tasks.named("check") {
    dependsOn(checkPerfThreshold)
}

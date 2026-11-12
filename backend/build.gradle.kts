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
    // P2 §4.5's [COPY] block is merged verbatim but is not valid OpenAPI (three operations without responses,
    // /branches/{branchId}/merge without a branchId parameter). Validation is off so the text stays verbatim; those
    // endpoints are hand-written controllers, so nothing depends on what the generator makes of them.
    skipValidateSpec.set(true)
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
    // P2 T5: reports (templates, Word, PDF)
    implementation(libs.freemarker)
    // docx4j pulls ANTLR 3.5 (and 2.7) for Word field codes, which the XHTML import does not use; ANTLR 3.5 would
    // replace the 3.2 that Eclipse OCL's Xtext parser needs (NoSuchFieldError Token.EOF_TOKEN in every OCL check).
    implementation(libs.docx4j.jaxb) {
        exclude(group = "org.antlr")
        exclude(group = "antlr")
    }
    implementation(libs.docx4j.import.xhtml) {
        exclude(group = "org.antlr")
        exclude(group = "antlr")
    }
    implementation(libs.openhtmltopdf.pdfbox)
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
    testImplementation(project(":polarion-sso")) // the extension's own assertion format, checked by PolarionSsoTest
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
    description = "Fails unless build/perf-refresh.json reports median < 800 ms and P95 < 2000 ms, build/perf-view-switch.json every switch < 1000 ms, and build/perf/benchmark-10k.json the P2 §6.1 limits."
    val report = layout.buildDirectory.file("perf-refresh.json")
    val viewSwitchReport = layout.buildDirectory.file("perf-view-switch.json")
    val benchmark10k = layout.buildDirectory.file("perf/benchmark-10k.json")
    dependsOn(tasks.named("test"))
    inputs.file(report)
    inputs.file(viewSwitchReport)
    inputs.file(benchmark10k)
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

        // P1 T9 step 3: 500 elements, SA/LA/PA open together, every view switch < 1 s (ViewSwitchPerfTest).
        val switches = viewSwitchReport.get().asFile
        if (!switches.isFile) {
            throw GradleException("perf gate: ${switches} is missing; ViewSwitchPerfTest did not run")
        }
        @Suppress("UNCHECKED_CAST")
        val switch = groovy.json.JsonSlurper().parse(switches) as Map<String, Any?>
        val switchElements = (switch["elements"] as Number).toInt()
        val views = (switch["views"] as Number).toInt()
        val worst = maxOf((switch["plainMaxMs"] as Number).toDouble(), (switch["afterEditMaxMs"] as Number).toDouble())
        if (switchElements < 500 || views < 3) {
            throw GradleException("perf gate: the view-switch scenario must be 500 elements and 3 views, was $switchElements and $views")
        }
        if (worst >= 1000.0) {
            throw GradleException("perf gate failed: a view switch took $worst ms (limit 1000)")
        }
        logger.lifecycle("perf gate passed: slowest view switch $worst ms ($switchElements elements, $views views)")

        // P2 T7 (spec P2 §6.1): 10,000 elements (TenThousandBenchmarkTest). The 30-editor P99 < 500 ms needs a running
        // backend (tests/perf/concurrent-writes.mjs) and the matrix's first screen a browser; both are measured live.
        val tenK = benchmark10k.get().asFile
        if (!tenK.isFile) {
            throw GradleException("perf gate: ${tenK} is missing; TenThousandBenchmarkTest did not run")
        }
        @Suppress("UNCHECKED_CAST")
        val big = groovy.json.JsonSlurper().parse(tenK) as Map<String, Any?>
        val bigElements = (big["elements"] as Number).toInt()
        if (bigElements < 10_000 || (big["mergeOperations"] as Number).toInt() < 200) {
            throw GradleException("perf gate: the benchmark must be 10,000 elements and a 200-change merge, was $bigElements and ${big["mergeOperations"]}")
        }
        val limits = linkedMapOf("ibdOpenColdMs" to 3000.0, "matrixColdMs" to 1500.0, "srsTotalMs" to 60000.0, "mergePreviewMs" to 10000.0)
        val over = limits.filter { (key, limit) -> (big[key] as Number).toDouble() >= limit }
        if (over.isNotEmpty()) {
            throw GradleException("perf gate failed (10k): " + over.map { (key, limit) -> "$key ${big[key]} ms (limit ${limit.toInt()})" })
        }
        logger.lifecycle("perf gate passed (10k): " + limits.keys.joinToString { "$it ${big[it]} ms" })
    }
}

tasks.named("check") {
    dependsOn(checkPerfThreshold)
}

// P1 T1 step 5: offline rollback of the interface migration (backend stopped). Dry run unless -Papply.
tasks.register<JavaExec>("rollbackInterfaceMigration") {
    group = "migration"
    description = "Restores Port.interfaceType strings from interface_migration_backup (dry run unless -Papply); run with the backend stopped."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.sysarch.migration.InterfaceMigrationRollback")
    if (project.hasProperty("apply")) {
        args("--apply")
    }
}

// P2 T7: the 10,000-element benchmark holds several copies of the model and its snapshots (512 MB by default ran out).
tasks.named<Test>("test") {
    maxHeapSize = "2g"
}

// P3 T4: the spec's solver script (solver/python_solver.py, kept verbatim at the repository root) ships with the
// backend as the classpath resource solver/python_solver.py; PythonSolver extracts it at start-up.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("solver/python_solver.py")) {
        into("solver")
    }
}

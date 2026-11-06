plugins {
    java
}

// VSM compiler (spec §4.5, T3): views/*.vsm.yaml -> backend/src/main/java/com/example/sysarch/view/generated.
// It has no dependency on the backend or on Sirius Web; it reads the metamodel from metamodel/sysarch.ecore.
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.snakeyaml)
    implementation(libs.freemarker)
    implementation(libs.jackson.databind)
    implementation(libs.json.schema.validator)
    implementation(libs.emf.common)
    implementation(libs.emf.ecore)
    implementation(libs.emf.ecore.xmi)
    runtimeOnly(libs.slf4j.nop) // the schema validator logs through SLF4J; keep compiler output to diagnostics only

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.context) // generated code uses @Component; the test compiles it
    testRuntimeOnly(libs.junit.platform.launcher)
}

val repoRoot: String = rootDir.absolutePath

tasks.register<JavaExec>("generateViews") {
    group = "codegen"
    description = "Compiles views/*.vsm.yaml into the backend's view.generated package (fails on any invalid view)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.vsm.VsmCompiler")
    args(repoRoot)
}

tasks.test {
    systemProperty("sysarch.repoRoot", repoRoot)
    // The tests read the real views, schema, metamodel, descriptor API and committed generated code.
    inputs.dir(rootDir.resolve("views"))
    inputs.file(rootDir.resolve("metamodel/sysarch.ecore"))
    inputs.dir(rootDir.resolve("backend/src/main/java/com/example/sysarch/view"))
}

// CI gate 2 (spec T9 step 3): two generations and the committed code must have the same sha256.
val verifyDeterministicGeneration = tasks.register<JavaExec>("verifyDeterministicGeneration") {
    group = "verification"
    description = "Generates the views twice and compares sha256 with each other and with the committed view/generated."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.vsm.DeterminismCheck")
    val workDir = layout.buildDirectory.dir("vsm-determinism")
    val report = layout.buildDirectory.file("vsm-generation.sha256")
    args(repoRoot, workDir.get().asFile.absolutePath, report.get().asFile.absolutePath)
    inputs.dir(rootDir.resolve("views"))
    inputs.file(rootDir.resolve("metamodel/sysarch.ecore"))
    inputs.dir(rootDir.resolve("backend/src/main/java/com/example/sysarch/view/generated"))
    outputs.file(report)
}

tasks.named("check") {
    dependsOn(verifyDeterministicGeneration)
}

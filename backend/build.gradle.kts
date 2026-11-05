plugins {
    java
    alias(libs.plugins.spring.boot)
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

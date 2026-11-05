plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    // Sirius Web is consumed as a binary dependency only; its source is never modified (spec §2.3).
    implementation(libs.sirius.web.starter)
    implementation(libs.spring.boot.starter.actuator)
    // Used directly by the generated sysarch model/edit code. Same versions Sirius Web brings.
    implementation(libs.bundles.emf.runtime)
    // The generated SysarchEditPlugin.Implementation extends EMFPlugin.EclipsePlugin, which is only loaded
    // inside OSGi. Its supertypes are needed to compile, never at runtime (same approach as Sirius' papaya-edit).
    compileOnly(libs.eclipse.core.runtime) { isTransitive = false }
    compileOnly(libs.eclipse.osgi) { isTransitive = false }

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

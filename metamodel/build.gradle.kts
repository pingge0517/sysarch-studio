plugins {
    java
}

// Headless EMF code generation (spec §3.3 / T2). The spec's `java -jar org.eclipse.emf.codegen.ecore.jar`
// needs an Eclipse runtime, so this module runs EMF's templates standalone instead (EmfCodeGenerator).
// The classpath is the exact jar set verified to work. Transitive resolution is off so Eclipse's open
// version ranges cannot drift (spec §11.2 #4: generation must be reproducible).
configurations.named { it == "compileClasspath" || it == "runtimeClasspath" }.configureEach {
    isTransitive = false
}

dependencies {
    implementation(libs.bundles.emf.codegen)
}

tasks.register<JavaExec>("generateModel") {
    group = "codegen"
    description = "Regenerates metamodel/sysarch.genmodel and the EMF model/edit code in backend/ from sysarch.ecore."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.sysarch.codegen.EmfCodeGenerator")
    args(rootDir.absolutePath)
}

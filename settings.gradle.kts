rootProject.name = "sysarch-studio"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Sirius Web is published only to GitHub Packages, which requires a token
        // (any GitHub PAT with read:packages). Set gpr.user/gpr.key in
        // ~/.gradle/gradle.properties, or export GITHUB_ACTOR/GITHUB_TOKEN.
        listOf("sirius-web", "sirius-emf-json").forEach { repo ->
            maven {
                name = "github-" + repo
                url = uri("https://maven.pkg.github.com/eclipse-sirius/$repo")
                credentials {
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR")).orElse("token").get()
                    password = providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
                }
                content { includeGroupByRegex("""org\.eclipse\.sirius(\..*)?""") }
            }
        }
        // Eclipse OCL (Pivot) is published only as a p2 update site, not to any Maven repository. Its plugin jars
        // are plain jars, so an artifact-only ivy layout fetches them directly. The pseudo-group keeps every other
        // dependency away from this repository.
        ivy {
            name = "eclipse-ocl-p2"
            url = uri("https://download.eclipse.org/modeling/mdt/ocl/updates/releases/6.19.0/plugins")
            patternLayout { artifact("[artifact]_[revision].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("eclipse.ocl.p2") }
        }
    }
}

include("metamodel", "backend", "vsm-compiler")

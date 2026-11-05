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
    }
}

include("backend", "vsm-compiler")

import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("legendary.java-conventions")
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jetbrains.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val coreJavaSources = fileTree("src") { include("**/*.java") }

val checkNoPlatformImports =
    tasks.register<ForbiddenTextCheck>("checkNoPlatformImports") {
        group = "verification"
        description = "Fails if core contains any com.hypixel.* import. core must stay engine-agnostic."
        sources.from(coreJavaSources)
        forbiddenText.set("com.hypixel.")
        headline.set("core must remain engine-agnostic, but %d file(s) import com.hypixel.*:")
        remedy.set("Platform-specific code belongs in :mod:hytale.")
        relativeTo.set(layout.projectDirectory)
        outputs.upToDateWhen { false }
    }

tasks.named("check") { dependsOn(checkNoPlatformImports) }

val checkNoQuestlineImports =
    tasks.register<ForbiddenTextCheck>("checkNoQuestlineImports") {
        group = "verification"
        description =
            "Heuristic secondary guard: fails if any file under src/ contains the literal " +
            "text \"legendary.quests.\" -- the package prefix EVERY questline uses, because " +
            "package paths mirror Gradle module paths (:quests:<name> -> legendary.quests.<name>). " +
            "Before the 2026-08-24 rename this needle was \"legendary.mod.\", which matched only " +
            "because Stormseeker happened to occupy that prefix; it was correct by accident and " +
            "would have matched nothing once a second questline arrived. It is still a plain text " +
            "scan, not import- or package-aware, and it does not read build scripts, so a " +
            "project() dependency on a questline is invisible to it. See checkNoQuestlineDependency " +
            "for the check that actually asserts core has no dependency on a questline project."
        sources.from(coreJavaSources)
        forbiddenText.set("legendary.quests.")
        headline.set("core must not reference a specific questline, but %d file(s) do:")
        remedy.set("Questline code belongs in :quests:<name>; register it from :mod:hytale.")
        relativeTo.set(layout.projectDirectory)
        outputs.upToDateWhen { false }
    }

tasks.named("check") { dependsOn(checkNoQuestlineImports) }

// Resolved during configuration rather than inside the task action: reaching for
// `configurations` at execution time is both a script-object reference and a Task.project
// invocation, and was one of the build's configuration-cache blockers.
val questlineProjectDependencies =
    provider {
        configurations.flatMap { configuration ->
            configuration.dependencies
                .withType(ProjectDependency::class.java)
                .filter { it.path.startsWith(":quests") }
                .map { "${configuration.name} -> ${it.path}" }
        }
    }

val checkNoQuestlineDependency =
    tasks.register<ForbiddenProjectDependencyCheck>("checkNoQuestlineDependency") {
        group = "verification"
        description =
            "Fails if :core declares a project dependency on any :quests:* project. " +
            "core hosts the questline framework; questlines depend on core, never the reverse."
        offenders.set(questlineProjectDependencies)
        headline.set("core must not depend on a specific questline project, but found %d such dependencies:")
        remedy.set(
            "Questline code belongs in :quests:<name>; it may depend on :core, not the other way around.",
        )
        outputs.upToDateWhen { false }
    }

tasks.named("check") { dependsOn(checkNoQuestlineDependency) }

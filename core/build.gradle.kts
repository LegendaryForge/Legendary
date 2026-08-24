import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("java-library")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jetbrains.annotations)
    errorprone(libs.errorprone.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                libs.versions.java
                    .get()
                    .toInt(),
            ),
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release =
        libs.versions.java
            .get()
            .toInt()

    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val checkNoPlatformImports by tasks.registering {
    group = "verification"
    description = "Fails if core contains any com.hypixel.* import. core must stay engine-agnostic."
    val javaSources = fileTree("src") { include("**/*.java") }
    inputs.files(javaSources)
    outputs.upToDateWhen { false }
    doLast {
        val offenders = javaSources.files.filter { it.readText().contains("com.hypixel.") }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("core must remain engine-agnostic, but ${offenders.size} file(s) import com.hypixel.*:")
                    offenders.forEach { appendLine("  " + it.relativeTo(projectDir)) }
                    appendLine("Platform-specific code belongs in :mod:hytale.")
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoPlatformImports) }

val checkNoQuestlineImports by tasks.registering {
    group = "verification"
    description =
        "Heuristic secondary guard: fails if any file under src/ contains the literal " +
        "text \"legendary.mod.\" (the package prefix questlines currently use). This is a " +
        "plain text scan, not an import- or package-aware check -- it cannot see a questline " +
        "outside the legendary.mod.* prefix, and it does not scan build scripts, so a " +
        "project() dependency on a questline is invisible to it. See checkNoQuestlineDependency " +
        "for the check that actually asserts core has no dependency on a questline project."
    val javaSources = fileTree("src") { include("**/*.java") }
    inputs.files(javaSources)
    outputs.upToDateWhen { false }
    doLast {
        val offenders = javaSources.files.filter { it.readText().contains("legendary.mod.") }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("core must not reference a specific questline, but ${offenders.size} file(s) do:")
                    offenders.forEach { appendLine("  " + it.relativeTo(projectDir)) }
                    appendLine("Questline code belongs in :quests:<name>; register it from :mod:hytale.")
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoQuestlineImports) }

val checkNoQuestlineDependency by tasks.registering {
    group = "verification"
    description =
        "Fails if :core declares a project dependency on any :quests:* project. " +
        "core hosts the questline framework; questlines depend on core, never the reverse."
    outputs.upToDateWhen { false }
    doLast {
        val offenders = mutableListOf<String>()
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val path = dependency.path
                if (path.startsWith(":quests")) {
                    offenders.add("${configuration.name} -> $path")
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "core must not depend on a specific questline project, but found ${offenders.size} such dependenc${if (offenders.size == 1) "y" else "ies"}:",
                    )
                    offenders.forEach { appendLine("  $it") }
                    appendLine("Questline code belongs in :quests:<name>; it may depend on :core, not the other way around.")
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoQuestlineDependency) }

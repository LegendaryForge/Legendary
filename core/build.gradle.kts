import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone

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
    description = "Fails if core references any specific questline. core hosts the questline framework, not questlines."
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

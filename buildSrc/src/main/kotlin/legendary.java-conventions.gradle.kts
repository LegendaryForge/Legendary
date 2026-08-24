import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("java-library")
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion =
    libs
        .findVersion("java")
        .get()
        .requiredVersion
        .toInt()
val palantirVersion = libs.findVersion("palantir").get().requiredVersion

dependencies {
    "errorprone"(libs.findLibrary("errorprone-core").get())
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = javaVersion
    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(palantirVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val moduleCoverage = extensions.create<ModuleCoverageExtension>("moduleCoverage")

val checkModuleCoverage =
    tasks.register<ModuleCoverageCheck>("checkModuleCoverage") {
        group = "verification"
        description =
            "Fails if this module has Java sources on disk but compiles none of them, " +
            "unless a moduleCoverage exemption is declared AND its predicate holds."
        modulePath.set(project.path)
        onDisk.set(
            provider {
                val srcDir = file("src/main/java")
                if (srcDir.exists()) fileTree(srcDir) { include("**/*.java") }.files.size else 0
            },
        )
        compiled.set(provider { (tasks.getByName("compileJava") as JavaCompile).source.files.size })
        exemptionReason.set(provider { moduleCoverage.exemptionReason })
        exemptionHolds.set(provider { moduleCoverage.exemptionPredicate?.invoke() ?: false })
        report.set(layout.buildDirectory.file("module-coverage.json"))
        outputs.upToDateWhen { false }
    }

tasks.named("check") { dependsOn(checkModuleCoverage) }

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
val javaVersion = libs.findVersion("java").get().requiredVersion.toInt()
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

val checkModuleCoverage by tasks.registering {
    group = "verification"
    description =
        "Fails if this module has Java sources on disk but compiles none of them, " +
        "unless a moduleCoverage exemption is declared AND its predicate holds."
    outputs.upToDateWhen { false }
    doLast {
        val srcDir = file("src/main/java")
        val onDisk =
            if (srcDir.exists()) fileTree(srcDir) { include("**/*.java") }.files.size else 0
        val compiled = (tasks.getByName("compileJava") as JavaCompile).source.files.size
        val reason = moduleCoverage.exemptionReason
        val exempt = moduleCoverage.exemptionPredicate?.invoke() ?: false

        val state =
            when {
                onDisk == 0 -> "EMPTY"
                compiled == 0 && reason == null -> "FAIL"
                compiled == 0 && !exempt -> "FAIL"
                compiled == 0 -> "EXEMPT"
                compiled < onDisk -> "PARTIAL"
                else -> "FULL"
            }

        val report = layout.buildDirectory.file("module-coverage.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            """{"module":"${project.path}","onDisk":$onDisk,"compiled":$compiled,""" +
                """"state":"$state","reason":${if (reason == null) "null" else "\"$reason\""}}""",
        )

        if (state == "FAIL") {
            throw GradleException(
                buildString {
                    appendLine("${project.path} has $onDisk Java source file(s) but compiled 0 of them.")
                    if (reason == null) {
                        appendLine("No moduleCoverage exemption is declared for this module.")
                        appendLine("Either fix the build so sources compile, or declare:")
                        appendLine("  moduleCoverage { zeroCompileAllowedWhen(\"why\") { condition } }")
                    } else {
                        appendLine("An exemption is declared (\"$reason\") but its predicate is FALSE,")
                        appendLine("so the exemption does not apply. This is a real breakage.")
                    }
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkModuleCoverage) }

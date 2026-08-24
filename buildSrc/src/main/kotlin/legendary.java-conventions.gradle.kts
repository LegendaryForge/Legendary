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

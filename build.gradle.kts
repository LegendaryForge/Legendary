import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `maven-publish`
    id("java-library")
    id("net.ltgt.errorprone") version "4.4.0"
    id("com.diffplug.spotless") version "8.2.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "io.github.legendaryforge"
            artifactId = "Legendary"
            version = "0.0.0-SNAPSHOT"
        }
    }
}

dependencies {
    // Resolved via composite build substitution (vendor/LegendaryCore).
    implementation("io.github.legendaryforge:LegendaryCore:0.0.0-SNAPSHOT")

    errorprone("com.google.errorprone:error_prone_core:2.27.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.85.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = 21

    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

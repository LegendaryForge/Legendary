plugins {
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

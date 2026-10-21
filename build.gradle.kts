plugins {
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Documentation organisation guard. Wired into `check` so it runs wherever the build runs --
// including CI, because it reads files rather than classes and so is meaningful in the one
// environment where `mod/hytale` compiles nothing. Read DOCS_VERDICT by name, not the exit code.
val checkDocsOrganization by tasks.registering(DocsOrganizationCheck::class) {
    group = "verification"
    description =
        "Asserts docs/ stays organised: curated docs indexed, dated docs prefixed, links resolve, specs declare status, verified docs carry a stamp"
    docsDir.set(layout.projectDirectory.dir("docs"))
}

tasks.named("check") {
    dependsOn(checkDocsOrganization)
}

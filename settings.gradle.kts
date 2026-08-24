plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Legendary"

includeBuild("vendor/LegendaryCore") {
    dependencySubstitution {
        substitute(module("com.example:LegendaryCore")).using(project(":"))
    }
}

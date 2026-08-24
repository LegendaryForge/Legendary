plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Legendary"

include(":core")
include(":quests:stormseeker")
include(":platform:hytale")

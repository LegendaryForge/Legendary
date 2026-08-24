plugins {
    id("legendary.java-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":quests:stormseeker"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

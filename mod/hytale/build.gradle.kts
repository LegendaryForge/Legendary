import java.util.zip.ZipFile

plugins {
    id("legendary.java-conventions")
    alias(libs.plugins.shadow)
}

// --- Hytale install detection (Linux) ---
val patchlineProp = (findProperty("patchline") as String?) ?: "release"
val hytaleHomeProp = findProperty("hytale_home") as String?

val hytaleHome =
    hytaleHomeProp ?: run {
        val flatpak = file(System.getProperty("user.home") + "/.var/app/com.hypixel.HytaleLauncher/data/Hytale")
        if (flatpak.exists()) {
            flatpak.absolutePath
        } else {
            val local = file(System.getProperty("user.home") + "/.local/share/Hytale")
            if (local.exists()) local.absolutePath else ""
        }
    }

val hytaleServerJar = file("$hytaleHome/install/$patchlineProp/package/game/latest/Server/HytaleServer.jar")
val hasHytaleServerJar = hytaleHome.isNotBlank() && hytaleServerJar.exists()

moduleCoverage {
    incompleteCompilationAllowedWhen("no Hytale server jar in this environment") { !hasHytaleServerJar }
}

val targetJava =
    libs.versions.java
        .get()
        .toInt()

if (!hasHytaleServerJar) {
    logger.lifecycle(
        "Hytale install not detected; skipping Server API jar. Set hytale_home in gradle.properties for local dev.",
    )
}

// The jar inspection lives in buildSrc (HytaleJarVersionCheck) rather than in a
// doLast here. The inline version captured script-level references and was the only
// configuration-cache blocker in the build. The onlyIf below is deliberate and must
// stay: it makes Gradle print `SKIPPED` for this task on a machine with no game jar,
// and agent/health_check.py's API-drift probe reads that exact line to distinguish
// "not observable here" (INFO) from "verified" (PASS).
val checkHytaleJarVersion =
    tasks.register<HytaleJarVersionCheck>("checkHytaleJarVersion") {
        description =
            "Verifies the installed Hytale server jar's class-file version is compatible " +
            "with this build's target Java version."
        if (hasHytaleServerJar) {
            serverJar.set(hytaleServerJar)
        }
        targetJavaVersion.set(targetJava)
        onlyIf { serverJar.isPresent }
    }

dependencies {
    api(project(":quests:stormseeker"))
    if (hasHytaleServerJar) {
        compileOnly(files(hytaleServerJar))
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // Without the Hytale jar these cannot compile; skip them so the rest of the
    // module still builds on a machine with no game installed.
    if (!hasHytaleServerJar) {
        exclude("**/hytale/**")
    }
}

tasks.named("compileJava") {
    dependsOn(checkHytaleJarVersion)
}

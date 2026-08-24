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

/**
 * Class-file major 65 = Java 21, 69 = Java 25. Returns null on genuine I/O errors
 * reading the jar. Throws if the jar has no com/hypixel classes at all — that is a
 * broken assumption, not an absent install, and must not be swallowed into a silent
 * `null` (see Task 3 review finding: keying on one fixed class path fails open if a
 * future Hytale patch renames or removes it).
 */
fun hytaleJarJavaVersion(jar: File): Int? {
    if (!jar.exists()) return null
    // Only genuine I/O errors (jar unreadable/corrupt) are swallowed to null. The
    // "no com/hypixel entry found" case throws GradleException and must NOT be
    // caught here — wrapping it in runCatching would silently disarm the guard
    // again, which is exactly the failure mode this hardening exists to prevent.
    return try {
        ZipFile(jar).use { zip ->
            val entry =
                zip.entries().asSequence().firstOrNull { e ->
                    e.name.startsWith("com/hypixel/") && e.name.endsWith(".class")
                }
                    ?: throw GradleException(
                        "Hytale jar contains no com/hypixel classes — cannot determine its Java version",
                    )
            zip.getInputStream(entry).use { input ->
                val header = input.readNBytes(8)
                if (header.size < 8) return null
                (((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)) - 44
            }
        }
    } catch (e: java.io.IOException) {
        null
    }
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

// Detection above (hasHytaleServerJar) stays at configuration time — it's a boolean
// used to shape the dependency set and compiled sources for this module. The actual
// jar inspection and both throws are deferred into this task's action so they run at
// *execution* time, scoped to this module's own tasks. Previously both throws sat in
// top-level script code, which meant they ran at *configuration* time for every task
// in the build — including e.g. `:core:build`, a module with zero Hytale involvement.
val checkHytaleJarVersion =
    tasks.register("checkHytaleJarVersion") {
        description =
            "Verifies the installed Hytale server jar's class-file version is compatible " +
            "with this build's target Java version."
        onlyIf { hasHytaleServerJar }
        doLast {
            val jarJava = hytaleJarJavaVersion(hytaleServerJar)
            if (jarJava != null && jarJava > targetJava) {
                throw GradleException(
                    """
                    |Hytale server jar requires Java $jarJava but this build targets Java $targetJava.
                    |The game was updated underneath the build; javac cannot read newer class files.
                    |Fix: set java = "$jarJava" in gradle/libs.versions.toml (and install a matching JDK).
                    |Jar: $hytaleServerJar
                    """.trimMargin(),
                )
            }
        }
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

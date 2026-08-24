import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails with one explicit message when the installed Hytale server jar is compiled for a
 * newer Java release than this build targets, instead of letting javac emit a cascade of
 * unrelated errors.
 *
 * Lives in buildSrc rather than in mod/hytale/build.gradle.kts because the previous
 * inline version captured script-level references (`hytaleJarJavaVersion`, `targetJava`)
 * in its `doLast`, which is not serializable and was the ONLY thing blocking the
 * configuration cache for the whole build.
 */
abstract class HytaleJarVersionCheck : DefaultTask() {

    /**
     * Absent on any machine without the game installed. The registering script keeps an
     * `onlyIf` on the same condition so Gradle prints `SKIPPED` for this task — that line
     * is load-bearing beyond the build: `agent/health_check.py`'s Hytale API-drift probe
     * reads it to tell "no jar here" apart from "verified", and reports INFO rather than
     * PASS when it appears. Do not remove the onlyIf in favour of an in-action bail.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val serverJar: RegularFileProperty

    @get:Input
    abstract val targetJavaVersion: Property<Int>

    @TaskAction
    fun verify() {
        val jar = serverJar.orNull?.asFile ?: return
        val target = targetJavaVersion.get()
        val jarJava = jarJavaVersion(jar) ?: return

        if (jarJava > target) {
            throw GradleException(
                """
                |Hytale server jar requires Java $jarJava but this build targets Java $target.
                |The game was updated underneath the build; javac cannot read newer class files.
                |Fix: set java = "$jarJava" in gradle/libs.versions.toml (and install a matching JDK).
                |Jar: $jar
                """.trimMargin(),
            )
        }
    }

    /**
     * Class-file major 65 = Java 21, 69 = Java 25. Returns null on genuine I/O errors
     * reading the jar.
     *
     * Throws if the jar holds no com/hypixel classes at all — that is a broken assumption,
     * not an absent install, and must not be swallowed into a silent `null`. Keying on one
     * fixed class path would fail open if a future Hytale patch renamed or removed it, so
     * the first com/hypixel entry of any name is used and its absence is fatal.
     */
    private fun jarJavaVersion(jar: File): Int? {
        if (!jar.exists()) return null
        return try {
            ZipFile(jar).use { zip ->
                val entry =
                    zip.entries().asSequence().firstOrNull { entry ->
                        entry.name.startsWith("com/hypixel/") && entry.name.endsWith(".class")
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
}

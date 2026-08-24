import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Asserts that the `Main` entrypoint named in the mod manifest actually exists in this
 * module's sources, with a matching package declaration.
 *
 * The manifest names the entrypoint as a STRING, so the compiler cannot see it. Rename or
 * move that package and the build stays green — it compiles, all tests pass, every other
 * guard is satisfied — while the produced mod simply fails to load in the game. Nothing in
 * CI or the test suite starts a server, so the first signal would be a human launching
 * Hytale and finding nothing there.
 *
 * That is a longer-fuse version of the silent-green failures this build already guards
 * against, and it is armed for any future package rename.
 *
 * Deliberately checks SOURCES, not compiled classes. `mod/hytale` compiles nothing on a
 * machine without the proprietary game jar — CI included — so a class-file check would be
 * vacuous in exactly the environment that runs on every push. Source files are there
 * regardless, so this guard is meaningful everywhere.
 */
abstract class ManifestEntrypointCheck : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val manifestFile = manifest.get().asFile

        val parsed = JsonSlurper().parse(manifestFile)
        if (parsed !is Map<*, *>) {
            throw GradleException("$manifestFile is not a JSON object")
        }

        val main = parsed["Main"] as? String
        if (main.isNullOrBlank()) {
            throw GradleException("$manifestFile declares no \"Main\" entrypoint")
        }

        val relativePath = main.replace('.', '/') + ".java"
        val match = sources.files.firstOrNull { it.path.replace('\\', '/').endsWith("/$relativePath") }
            ?: throw GradleException(
                """
                |Mod manifest names an entrypoint that does not exist in this module.
                |  manifest: $manifestFile
                |  Main:     $main
                |  expected: src/main/java/$relativePath
                |The manifest refers to the class by NAME, so the compiler cannot catch this.
                |A build with a wrong Main is green in every other respect and produces a mod
                |that will not load. If the package was renamed, update the manifest too.
                """.trimMargin(),
            )

        // Path can match while the package declaration does not, e.g. a file copied to a new
        // directory with its old `package` line intact. Java would refuse that, but only for
        // sources this module actually compiles -- and it compiles none without the game jar.
        val expectedPackage = main.substringBeforeLast('.')
        val declaresPackage = match.readLines().any { it.trim() == "package $expectedPackage;" }
        if (!declaresPackage) {
            throw GradleException(
                """
                |Mod manifest entrypoint found at the expected path, but the file does not
                |declare the matching package.
                |  file:     $match
                |  expected: package $expectedPackage;
                """.trimMargin(),
            )
        }
    }
}

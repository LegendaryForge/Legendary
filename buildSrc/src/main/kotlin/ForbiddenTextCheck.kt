import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when any scanned file contains a forbidden literal.
 *
 * Replaces two near-identical `doLast` blocks in core/build.gradle.kts. Those captured
 * script-level references (the FileTree val and `projectDir`) and so could not be stored
 * in the configuration cache.
 *
 * This is a plain text scan and nothing more. It is not import- or package-aware, does not
 * parse Java, and does not look at build scripts — a dependency declared in a build file is
 * invisible to it. See ForbiddenProjectDependencyCheck for the structural counterpart.
 */
abstract class ForbiddenTextCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The literal that must not appear. Matched with String.contains, not as a regex. */
    @get:Input
    abstract val forbiddenText: Property<String>

    /** Rendered with the offender count substituted for `%d`. */
    @get:Input
    abstract val headline: Property<String>

    /** Closing line telling the reader where the code should live instead. */
    @get:Input
    abstract val remedy: Property<String>

    /** Message-only: offender paths are printed relative to this. */
    @get:Internal
    abstract val relativeTo: DirectoryProperty

    @TaskAction
    fun scan() {
        val needle = forbiddenText.get()
        val offenders = sources.files.filter { it.isFile && it.readText().contains(needle) }
        if (offenders.isEmpty()) {
            return
        }

        val base = relativeTo.get().asFile
        throw GradleException(
            buildString {
                appendLine(headline.get().replace("%d", offenders.size.toString()))
                offenders.forEach { appendLine("  " + it.relativeTo(base)) }
                appendLine(remedy.get())
            },
        )
    }
}

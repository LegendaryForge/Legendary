import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a module declares a project dependency it must not have.
 *
 * The offending dependencies are resolved into plain strings while the build is being
 * configured and handed over as an @Input, rather than the task reaching for
 * `configurations` at execution time — that reach was both a script-object reference and a
 * `Task.project` invocation, either of which is enough to stop the configuration cache.
 *
 * Structural, not textual: it reads the dependency graph, so it is name-independent and a
 * new questline cannot escape it by being called something else.
 */
abstract class ForbiddenProjectDependencyCheck : DefaultTask() {

    /** Pre-rendered `configurationName -> :project:path` entries; empty means clean. */
    @get:Input
    abstract val offenders: ListProperty<String>

    @get:Input
    abstract val headline: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val remedy: org.gradle.api.provider.Property<String>

    @TaskAction
    fun verify() {
        val found = offenders.get()
        if (found.isEmpty()) {
            return
        }
        throw GradleException(
            buildString {
                appendLine(headline.get().replace("%d", found.size.toString()))
                found.forEach { appendLine("  $it") }
                appendLine(remedy.get())
            },
        )
    }
}

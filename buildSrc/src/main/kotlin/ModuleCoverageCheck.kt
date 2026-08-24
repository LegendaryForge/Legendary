import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a module has Java sources on disk but compiles none of them, unless an
 * exemption is declared AND its predicate holds.
 *
 * Exists because `mod/hytale` legitimately compiles nothing on a machine without the
 * proprietary game jar — it excludes every source under a `hytale` package directory,
 * which is 100% of its sources, and
 * still reports BUILD SUCCESSFUL. That gap is declared and reported as EXEMPT on every run
 * rather than passing silently, while an *undeclared* zero-compile fails the build.
 *
 * All inputs are plain values resolved during configuration. The previous version read
 * `file(...)`, `fileTree(...)` and `tasks.getByName("compileJava")` inside its action, which
 * meant script-object references plus a `Task.project` invocation at execution time — the
 * last configuration-cache blockers in this build.
 */
abstract class ModuleCoverageCheck : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Java files present under src/main/java. */
    @get:Input
    abstract val onDisk: Property<Int>

    /** Java files compileJava actually took as source, after any excludes. */
    @get:Input
    abstract val compiled: Property<Int>

    @get:Input
    @get:Optional
    abstract val exemptionReason: Property<String>

    /** The declared predicate's value. A declaration alone never excuses a real breakage. */
    @get:Input
    abstract val exemptionHolds: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val path = modulePath.get()
        val onDiskCount = onDisk.get()
        val compiledCount = compiled.get()
        val reason = exemptionReason.orNull
        val exempt = exemptionHolds.get()

        // Truth table carried over verbatim from the approved spec. Note EMPTY is tested
        // FIRST and requires no declaration, so a module whose src/main/java disappears
        // reports 0/0 EMPTY and counts green. That is a known open question, deliberately
        // left alone here: this change is a mechanical conversion, and altering the table
        // would contradict the spec under cover of a configuration-cache fix.
        val state =
            when {
                onDiskCount == 0 -> "EMPTY"
                compiledCount == 0 && reason == null -> "FAIL"
                compiledCount == 0 && !exempt -> "FAIL"
                compiledCount == 0 -> "EXEMPT"
                compiledCount < onDiskCount -> "PARTIAL"
                else -> "FULL"
            }

        // Escape backslashes and double quotes before interpolating reason into hand-built
        // JSON -- backslash first, then quote, so a reason containing either does not emit
        // invalid JSON that breaks json.load() in the census.
        val escapedReason = reason?.replace("\\", "\\\\")?.replace("\"", "\\\"")

        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """{"module":"$path","onDisk":$onDiskCount,"compiled":$compiledCount,""" +
                """"state":"$state","reason":${if (escapedReason == null) "null" else "\"$escapedReason\""}}""",
        )

        if (state == "FAIL") {
            throw GradleException(
                buildString {
                    appendLine("$path has $onDiskCount Java source file(s) but compiled 0 of them.")
                    if (reason == null) {
                        appendLine("No moduleCoverage exemption is declared for this module.")
                        appendLine("Either fix the build so sources compile, or declare:")
                        appendLine("  moduleCoverage { zeroCompileAllowedWhen(\"why\") { condition } }")
                    } else {
                        appendLine("An exemption is declared (\"$reason\") but its predicate is FALSE,")
                        appendLine("so the exemption does not apply. This is a real breakage.")
                    }
                },
            )
        }
    }
}

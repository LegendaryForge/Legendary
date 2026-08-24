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

        // EVERY shortfall requires a declaration (operator decisions, 2026-08-24). FULL is
        // the only state that needs nothing said about it; PARTIAL, EXEMPT and EMPTY all
        // describe a module compiling less than its source set, and each fails when
        // undeclared. Only EXEMPT originally did.
        //
        // Both gaps were closed because both were reachable by ordinary work rather than
        // sabotage, and both failed GREEN:
        //   EMPTY   — move every source out of a module and a FAIL-worthy condition became
        //             a green 0/0. Demonstrated by deleting mod/hytale/src/main/java.
        //   PARTIAL — add ONE portable file to mod/hytale and it flips from
        //             "0/7 EXEMPT (no Hytale server jar...)" to a bare "1/8 PARTIAL" on any
        //             jar-less machine. Seven of eight sources stop compiling, and the
        //             declaration the guard exists to print silently stops being printed.
        //
        // Together these restore decision D1 — "it must be impossible for a NEW module to go
        // dark unnoticed" — which the original table undercut twice: EMPTY exempted the one
        // state a brand-new module is in, and PARTIAL let a declared module drift out of its
        // declaration by growing a single file.
        val state =
            when {
                onDiskCount == 0 && reason == null -> "FAIL"
                onDiskCount == 0 && !exempt -> "FAIL"
                onDiskCount == 0 -> "EMPTY"
                compiledCount == 0 && reason == null -> "FAIL"
                compiledCount == 0 && !exempt -> "FAIL"
                compiledCount == 0 -> "EXEMPT"
                compiledCount < onDiskCount && reason == null -> "FAIL"
                compiledCount < onDiskCount && !exempt -> "FAIL"
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
                    // The two FAIL causes need different remedies, so they get different
                    // messages. "has 0 source files but compiled 0 of them" would be true
                    // and useless.
                    if (onDiskCount == 0) {
                        appendLine("$path has no Java sources at all, so it contributes nothing.")
                        if (reason == null) {
                            appendLine("If the module is newly scaffolded and its sources are not written yet,")
                            appendLine("declare that:")
                            appendLine("  moduleCoverage { incompleteCompilationAllowedWhen(\"sources not written yet\") { true } }")
                            appendLine("If it previously had sources, they have been moved or deleted — the")
                            appendLine("module is now dead weight in settings.gradle.kts.")
                        } else {
                            appendLine("An exemption is declared (\"$reason\") but its predicate is FALSE,")
                            appendLine("so the exemption does not apply.")
                        }
                    } else if (compiledCount == 0) {
                        appendLine("$path has $onDiskCount Java source file(s) but compiled 0 of them.")
                        if (reason == null) {
                            appendLine("No moduleCoverage declaration exists for this module.")
                            appendLine("Either fix the build so sources compile, or declare:")
                            appendLine("  moduleCoverage { incompleteCompilationAllowedWhen(\"why\") { condition } }")
                        } else {
                            appendLine("A declaration exists (\"$reason\") but its predicate is FALSE,")
                            appendLine("so it does not apply. This is a real breakage.")
                        }
                    } else {
                        appendLine(
                            "$path compiled $compiledCount of its $onDiskCount Java source file(s) — " +
                                "${onDiskCount - compiledCount} were excluded.",
                        )
                        if (reason == null) {
                            appendLine("A module may compile less than its source set, but it must say why.")
                            appendLine("Find the exclude responsible and either remove it, or declare:")
                            appendLine("  moduleCoverage { incompleteCompilationAllowedWhen(\"why\") { condition } }")
                        } else {
                            appendLine("A declaration exists (\"$reason\") but its predicate is FALSE,")
                            appendLine("so it does not apply. This is a real breakage.")
                        }
                    }
                },
            )
        }
    }
}

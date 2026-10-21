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
 * Asserts that no two commands claim the same name, and that the boot log names exactly the
 * commands that are registered.
 *
 * Both halves were violated simultaneously, and neither was visible from anything the build
 * already checked.
 *
 * `CommandManager.register` is a plain `Map.put(command.getName(), command)` whose return value is
 * **popped** — no duplicate check, no warning, no exception. So two commands declaring the same
 * name is not an error at runtime: the last registration silently wins. On 2026-10-21
 * `ResidueParticleProbeCommand` and `ResidueSiteProbeCommand` both declared `residueprobe`, and the
 * site probe won only because it happened to be registered second. Reorder those two lines — or add
 * a questline that registers earlier — and `/residueprobe` becomes a particle emitter while every
 * verdict line in this build stays green. A session then spends its time measuring a particle
 * emitter and reports the result as a terrain finding.
 *
 * The boot log is the only place a human sees what got registered, which makes it the only
 * available signal — and it had drifted: 13 commands listed against 15 registered, with
 * `/weatheroverrideprobe` registered and never logged. A log that under-reports is worse than no
 * log, because it reads as an inventory.
 *
 * Deliberately checks SOURCES, not compiled classes or runtime state, for the same reason
 * [ManifestEntrypointCheck] does: `mod/hytale` compiles nothing on a machine without the
 * proprietary game jar — CI included — so a class-file check would be vacuous in exactly the
 * environment that runs on every push.
 *
 * Prints `COMMANDS_VERDICT:` as its last line. Read it by name, never by position.
 */
abstract class CommandRegistrationCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The class whose `start()` registers the commands and logs the inventory. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val plugin: RegularFileProperty

    @TaskAction
    fun verify() {
        val failures = mutableListOf<String>()

        // --- declared names, one per command class -------------------------------------------
        // The first string literal passed to super(...) is the command name. Matched across
        // newlines because a long declaration is wrapped by the formatter.
        val superName = Regex("""super\(\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        val nameByClass = mutableMapOf<String, String>()
        val classesByName = mutableMapOf<String, MutableList<String>>()
        for (file in sources.files.filter { it.name.endsWith("Command.java") }) {
            val name = superName.find(file.readText())?.groupValues?.get(1) ?: continue
            val simpleName = file.name.removeSuffix(".java")
            nameByClass[simpleName] = name
            classesByName.getOrPut(name) { mutableListOf() }.add(simpleName)
        }

        if (nameByClass.isEmpty()) {
            // A guard that found nothing to check must fail rather than report success. An empty
            // sweep and a clean sweep are indistinguishable in the output, and this build has been
            // bitten by that shape repeatedly.
            throw GradleException(
                "CommandRegistrationCheck found no *Command.java sources declaring a name. " +
                    "Either the source set is wrong or the super(\"name\") convention changed; " +
                    "either way this guard is no longer checking anything.",
            )
        }

        // --- rule 1: no two commands claim one name -----------------------------------------
        for ((name, classes) in classesByName.filter { it.value.size > 1 }) {
            failures.add(
                """
                |Two or more commands declare the name "$name":
                |  ${classes.sorted().joinToString("\n  ")}
                |CommandManager.register is a Map.put with the return value popped, so this does
                |not throw -- the last registration silently wins and the winner depends on the
                |order of the registerCommand calls. Rename all but one.
                """.trimMargin(),
            )
        }

        // --- rule 2: the boot log is an accurate inventory ------------------------------------
        val pluginFile = plugin.get().asFile
        val pluginSource = pluginFile.readText()

        val registeredClasses =
            Regex("""registerCommand\(\s*new\s+(\w+)\s*\(""", RegexOption.DOT_MATCHES_ALL)
                .findAll(pluginSource)
                .map { it.groupValues[1] }
                .toList()

        if (registeredClasses.isEmpty()) {
            throw GradleException(
                "CommandRegistrationCheck found no registerCommand(new X()) calls in $pluginFile. " +
                    "The registration shape changed and this guard can no longer see it.",
            )
        }

        val unresolved = registeredClasses.filterNot { nameByClass.containsKey(it) }
        if (unresolved.isNotEmpty()) {
            // Cannot verify these, so do not claim to have. Reporting GREEN while silently
            // skipping a registration is the failure mode this guard exists to prevent.
            failures.add(
                "Registered classes whose declared command name could not be read: " +
                    "${unresolved.sorted()}. Each must be a *Command.java declaring super(\"name\").",
            )
        }

        val registeredNames = registeredClasses.mapNotNull { nameByClass[it] }.toSortedSet()

        val logLine = Regex(""""Commands registered:([^"]*)"""").find(pluginSource)
        if (logLine == null) {
            failures.add(
                "No \"Commands registered: ...\" log literal found in $pluginFile. It is the only " +
                    "place an operator sees what registered, so its absence is a failure, not a pass.",
            )
        } else {
            val logged = Regex("""/([A-Za-z][A-Za-z0-9-]*)""").findAll(logLine.groupValues[1])
                .map { it.groupValues[1] }
                .toSortedSet()

            val missing = registeredNames - logged
            val phantom = logged - registeredNames
            if (missing.isNotEmpty()) {
                failures.add(
                    "Registered but absent from the boot log: ${missing.toList()}. " +
                        "A log that under-reports reads as an inventory and is worse than none.",
                )
            }
            if (phantom.isNotEmpty()) {
                failures.add(
                    "Named in the boot log but not registered: ${phantom.toList()}. " +
                        "The log promises a command the server does not have.",
                )
            }
        }

        val summary =
            "${nameByClass.size} command class(es) | ${registeredClasses.size} registered | " +
                "${classesByName.size} distinct name(s)"

        if (failures.isEmpty()) {
            logger.lifecycle("COMMANDS_VERDICT: GREEN | $summary")
            return
        }
        logger.lifecycle("COMMANDS_VERDICT: RED | ${failures.size} failure(s) | $summary")
        throw GradleException(
            "Command registration check failed:\n\n" + failures.joinToString("\n\n"),
        )
    }
}

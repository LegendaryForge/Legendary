import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Asserts that `docs/` stays organised, because every documentation failure this project has
 * recorded was content in the WRONG PLACE rather than content missing.
 *
 * On 2026-10-21 alone: engine API facts were living in design specs because no engine reference
 * existed that was not two builds stale; the lore brief asserted "there are no runes" as a verified
 * gap, true of build 0.5.9 and false of 0.7.2; four committed specs described a superseded model
 * while declaring themselves "decided"; and a load-bearing read-point sat in a file no repository
 * tracked. Each instance was fixed by hand. None of the fixes were enforced, so the next one would
 * have slipped the same way — which is what this task is for.
 *
 * The rules are deliberately mechanical. Five were proposed; the set below is what survived
 * MEASURING the existing tree first, which mattered: a proposed "every doc must be indexed in
 * docs/README.md" rule would have demanded 22 new index entries and turned the index into a
 * changelog, because specs and plans are append-only classes that accumulate indefinitely. Scoping
 * indexing to the CURATED directories and requiring a date prefix on the dated ones covers the same
 * discoverability concern without that cost. Four of the five rules had zero violations when
 * written, so this guard holds a line that already exists rather than imposing a new one.
 *
 * Prints `DOCS_VERDICT:` as its last line. Read it by name — this workspace has recorded five
 * separate occasions where something appended output after a verdict and the reader saw the wrapper
 * instead, and the failure always reads GREEN.
 *
 * Reads files, not classes, so it is meaningful on CI — where `mod/hytale` compiles nothing.
 */
abstract class DocsOrganizationCheck : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docsDir: DirectoryProperty

    /** Directories whose contents are hand-curated and must therefore be indexed in `README.md`. */
    private val curated = listOf(".", "architecture", "integration", "setting", "stormseeker")

    /** Directories of dated, append-only documents. Indexed by naming convention, not by README. */
    private val dated = listOf("superpowers/specs", "superpowers/plans")

    /**
     * Directories holding no documents of their own, or holding superseded ones kept as record.
     * Listing them is not a formality: rule 6 below fails on any directory that appears in neither
     * [curated], [dated] nor here, so a new directory cannot go unchecked by being overlooked.
     *
     * `architecture/` was omitted from [curated] in the first version of this task and was therefore
     * covered by no rule at all — five documents silently unguarded, found only by enumerating the
     * tree by hand. Rule 6 exists because that gap is the one this whole task is supposed to prevent,
     * and the task had it.
     */
    private val exempt = listOf("superpowers", "stormseeker/archive")

    /** Documents whose claims are verified against something that changes under them. */
    private val stamped = listOf("integration", "setting")

    private val datePrefix = Regex("""^\d{4}-\d{2}-\d{2}-""")
    private val linkPattern = Regex("""\[[^\]]*]\(([^)#]+?)(?:#[^)]*)?\)""")

    @TaskAction
    fun verify() {
        val docs = docsDir.get().asFile
        if (!docs.isDirectory) {
            throw GradleException("docs directory not found: $docs")
        }
        val readme = File(docs, "README.md")
        if (!readme.isFile) {
            throw GradleException("docs/README.md is missing; it is the index every curated doc must appear in")
        }
        val readmeText = readme.readText()
        val failures = mutableListOf<String>()

        // 1. Curated directories are indexed. A document nobody links to has no first reader.
        var curatedCount = 0
        for (dir in curated) {
            val d = if (dir == ".") docs else File(docs, dir)
            d.listFiles { f: File -> f.isFile && f.name.endsWith(".md") && f.name != "README.md" }
                ?.sortedBy { it.name }
                ?.forEach {
                    curatedCount++
                    val rel = it.relativeTo(docs).path.replace('\\', '/')
                    if (!readmeText.contains(rel)) {
                        failures += "unindexed curated doc: $rel is not mentioned in docs/README.md"
                    }
                }
        }

        // 2. Dated directories follow the date prefix, which is what makes them discoverable
        //    without a hand-maintained index.
        var datedCount = 0
        for (dir in dated) {
            File(docs, dir).listFiles { f: File -> f.isFile && f.name.endsWith(".md") }
                ?.sortedBy { it.name }
                ?.forEach {
                    datedCount++
                    if (!datePrefix.containsMatchIn(it.name)) {
                        failures += "undated doc: $dir/${it.name} must start YYYY-MM-DD-"
                    }
                }
        }

        // 3. Relative links resolve. This is the stale-pointer class, and it is the one rule here
        //    that catches a defect introduced by a correct edit elsewhere: moving a file breaks
        //    every document that pointed at it, and nothing else reports that.
        var linkCount = 0
        docs.walkTopDown().filter { it.isFile && it.name.endsWith(".md") }.forEach { file ->
            linkPattern.findAll(file.readText()).forEach { m ->
                val target = m.groupValues[1].trim()
                if (target.isEmpty() || target.startsWith("http://") ||
                    target.startsWith("https://") || target.startsWith("mailto:")
                ) {
                    return@forEach
                }
                linkCount++
                if (!File(file.parentFile, target).canonicalFile.exists()) {
                    val rel = file.relativeTo(docs).path.replace('\\', '/')
                    failures += "broken link: $rel -> $target"
                }
            }
        }

        // 4. A spec declares its status. Four specs read as current while describing a superseded
        //    model; the only thing a direct reader saw was that stale framing.
        var specCount = 0
        File(docs, "superpowers/specs")
            .listFiles { f: File -> f.isFile && f.name.endsWith(".md") }
            ?.sortedBy { it.name }
            ?.forEach {
                specCount++
                if (!it.readText().contains("**Status")) {
                    failures += "spec without status: superpowers/specs/${it.name} needs a \"**Status:\" line"
                }
            }

        // 5. A document verified against a moving target says what it was verified against. The
        //    lore brief's "[gap]" claims were true of 0.5.9 and read as timeless.
        var stampCount = 0
        for (dir in stamped) {
            File(docs, dir).listFiles { f: File -> f.isFile && f.name.endsWith(".md") }
                ?.sortedBy { it.name }
                ?.forEach {
                    stampCount++
                    val head = it.readText().take(4000)
                    val hasStamp = Regex("""\d+\.\d+\.\d+""").containsMatchIn(head) ||
                        head.contains("verified", ignoreCase = true) ||
                        head.contains("audited", ignoreCase = true) ||
                        head.contains("Build", ignoreCase = false)
                    if (!hasStamp) {
                        failures += "unstamped doc: $dir/${it.name} must say which build or date its claims " +
                            "were verified against, in its first 4000 characters"
                    }
                }
        }

        // 6. Every directory under docs/ is classified. Without this, adding a directory means
        //    adding documents that no rule examines -- which is how `architecture/` sat unguarded.
        val classified = (curated + dated + exempt).toSet()
        var dirCount = 0
        docs.walkTopDown().filter { it.isDirectory }.forEach { dir ->
            val rel = if (dir == docs) "." else dir.relativeTo(docs).path.replace('\\', '/')
            dirCount++
            if (rel !in classified) {
                failures += "unclassified directory: docs/$rel is in neither curated, dated nor exempt -- " +
                    "its documents are examined by no rule. Add it to DocsOrganizationCheck."
            }
        }

        val summary =
            "dirs=$dirCount curated=$curatedCount dated=$datedCount links=$linkCount " +
                "specs=$specCount stamped=$stampCount"
        if (failures.isEmpty()) {
            logger.lifecycle("DOCS_VERDICT: GREEN | $summary")
            return
        }
        logger.lifecycle("DOCS_VERDICT: RED | ${failures.size} failure(s) | $summary")
        failures.forEach { logger.lifecycle("  - $it") }
        throw GradleException(
            """
            |docs/ organisation check failed with ${failures.size} failure(s):
            |${failures.joinToString("\n") { "  - $it" }}
            |
            |Where things go is documented in docs/README.md -> "Where does this go?".
            |These rules exist because every documentation failure recorded in this project was
            |content in the wrong place, not content missing -- and each instance was fixed by hand
            |while the next one slipped the same way.
            """.trimMargin(),
        )
    }
}

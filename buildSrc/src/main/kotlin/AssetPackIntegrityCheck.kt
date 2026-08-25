import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Asserts that this module's shipped asset pack cannot silently change the base game.
 *
 * Hytale resolves an asset's id from its FILENAME, not from a field inside the file, and
 * `AssetModule` treats a mod file whose name matches a base asset as a deliberate override.
 * The same is true of translations: a `.lang` key we ship replaces the base game's text for
 * every player on the server, including players who never touch this questline. Both are
 * designed engine features, both are invisible to the compiler, and both fail GREEN — the
 * build passes, the tests pass, and the game is quietly different.
 *
 * `stormseeker-canonical.md` states the rule as design intent ("Recontextualise, never
 * rewrite"). This task is the mechanism. It enforces the rule by requiring every asset
 * filename and every translation key we ship to carry a Stormseeker-scoped prefix, which no
 * base-game id uses — so the check needs no copy of the base asset list and stays correct
 * across game updates.
 *
 * It also catches the two ways this content can be broken without the compiler noticing: an
 * objective line naming an objective that does not exist, and a title/description key with
 * no translation behind it (which renders as a raw key to the player).
 *
 * Deliberately reads FILES, not classes. `mod/hytale` compiles nothing on a machine without
 * the proprietary game jar — CI included — so a class- or test-based check would be vacuous
 * in exactly the environment that runs on every push. Same reasoning as ManifestEntrypointCheck.
 */
abstract class AssetPackIntegrityCheck : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceRoot: DirectoryProperty

    private val assetIdPrefixes = listOf(
        "ObjectiveLine_Stormseeker_",
        "Objective_Stormseeker_",
        "ReachLocationMarker_Stormseeker_",
        "ObjectiveLocationMarker_Stormseeker_",
        "Furniture_Stormseeker_",
    )

    private val langKeyPrefixes = listOf(
        "objectives.Objective_Stormseeker_",
        "objectivelines.ObjectiveLine_Stormseeker_",
        "items.Furniture_Stormseeker_",
        "stormseeker.",
    )

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()

        val manifestFile = manifest.get().asFile
        val parsed = JsonSlurper().parse(manifestFile)
        if (parsed !is Map<*, *>) throw GradleException("$manifestFile is not a JSON object")
        val includesAssetPack = parsed["IncludesAssetPack"] == true

        val root = resourceRoot.get().asFile
        val serverDir = File(root, "Server")

        if (serverDir.isDirectory && !includesAssetPack) {
            problems += "Server/ asset tree exists but manifest.json declares " +
                "\"IncludesAssetPack\": false, so the server will never load these assets."
        }
        if (!serverDir.isDirectory && includesAssetPack) {
            problems += "manifest.json declares \"IncludesAssetPack\": true but there is " +
                "no Server/ tree at ${serverDir.path}."
        }
        if (!serverDir.isDirectory) {
            report(problems, manifestFile)
            return
        }

        val languagesDir = File(serverDir, "Languages")
        val assetFiles = serverDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filterNot { it.startsWith(languagesDir) }
            .toList()

        // Rule 1 — every asset id we ship is scoped to us, so it cannot shadow a base asset.
        for (file in assetFiles) {
            val assetId = file.nameWithoutExtension
            if (assetIdPrefixes.none { assetId.startsWith(it) }) {
                problems += "asset id '$assetId' (${file.relativeTo(root)}) does not start with " +
                    "one of $assetIdPrefixes — an unscoped id may override a base-game asset."
            }
        }

        // Rule 2 — objective lines must name objectives that exist.
        val objectivesDir = File(serverDir, "Objective/Objectives")
        val linesDir = File(serverDir, "Objective/ObjectiveLines")
        if (linesDir.isDirectory) {
            for (file in linesDir.listFiles { f: File -> f.extension == "json" }.orEmpty()) {
                val line = JsonSlurper().parse(file) as? Map<*, *>
                    ?: run { problems += "${file.name} is not a JSON object"; continue }
                val ids = line["ObjectiveIds"] as? List<*>
                if (ids.isNullOrEmpty()) {
                    problems += "${file.name} has an empty \"ObjectiveIds\" — the engine's own " +
                        "nonEmptyArray validator rejects this at boot."
                    continue
                }
                for (id in ids.filterIsInstance<String>()) {
                    if (!id.startsWith("Objective_Stormseeker_")) continue // a base-game objective
                    if (!File(objectivesDir, "$id.json").isFile) {
                        problems += "${file.name} names objective '$id', which has no file at " +
                            "Server/Objective/Objectives/$id.json"
                    }
                }
            }
        }

        // Rule 3 — our translations may only define our own keys.
        val langFile = File(languagesDir, "en-US/server.lang")
        val translations = mutableMapOf<String, String>()
        if (langFile.isFile) {
            langFile.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val split = line.indexOf('=')
                if (split < 0) {
                    problems += "${langFile.name}:${index + 1} is neither blank, a comment, nor 'key = value'"
                    return@forEachIndexed
                }
                val key = line.substring(0, split).trim()
                val value = line.substring(split + 1).trim()
                if (langKeyPrefixes.none { key.startsWith(it) }) {
                    problems += "${langFile.name}:${index + 1} defines '$key', which does not start " +
                        "with one of $langKeyPrefixes — this would override shipped text for every " +
                        "player on the server."
                }
                if (translations.put(key, value) != null) {
                    problems += "${langFile.name}:${index + 1} redefines '$key'"
                }
            }
        }

        // Rule 4 — every key our assets reference must have a translation behind it.
        // A key may be written with or without the file's own 'server.' prefix; both resolve
        // to the same entry, and which form the engine wants is settled empirically in Task 5.
        fun resolves(key: String) =
            translations.containsKey(key) || translations.containsKey(key.removePrefix("server."))

        for (file in assetFiles) {
            val asset = JsonSlurper().parse(file) as? Map<*, *> ?: continue
            collectKeys(asset).forEach { key ->
                if (!resolves(key)) {
                    problems += "${file.relativeTo(root)} references translation key '$key', " +
                        "which is not defined in Server/Languages/en-US/server.lang — it would " +
                        "render to the player as the raw key."
                }
            }
        }

        report(problems, manifestFile)
    }

    /** Every translation key an asset can carry, at any nesting depth. */
    private fun collectKeys(node: Any?): List<String> = when (node) {
        is Map<*, *> -> node.entries.flatMap { (k, v) ->
            if ((k == "TitleId" || k == "DescriptionId" || k == "Key" ||
                    (k == "Name" && v is String && v.startsWith("server."))) && v is String
            ) {
                listOf(v)
            } else {
                collectKeys(v)
            }
        }
        is List<*> -> node.flatMap { collectKeys(it) }
        else -> emptyList()
    }

    private fun report(problems: List<String>, manifestFile: File) {
        if (problems.isEmpty()) return
        throw GradleException(
            buildString {
                appendLine("Asset pack integrity check failed (${problems.size} problem(s)).")
                appendLine("  module manifest: $manifestFile")
                problems.forEach { appendLine("  - $it") }
            }.trimEnd(),
        )
    }
}

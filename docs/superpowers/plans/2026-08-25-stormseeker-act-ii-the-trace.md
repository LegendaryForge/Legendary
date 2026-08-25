# Stormseeker Act II — The Trace — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Stormseeker Act II — The Trace as real, playable content authored entirely as Hytale asset JSON, so that reading an inscription in a ruin starts the questline and reading the rest completes the act.

**Architecture:** `mod/hytale` becomes an asset pack (`IncludesAssetPack: true`) shipping a `Server/` tree in its own jar. The questline spine — line, objectives, tasks, per-player progress — is native Hytale objectives. Four custom block items carry the builders' inscriptions through `SendMessageInteraction`, and the first of them chains `Next` → `StartObjective` to begin the line. All player-facing text lives in one `server.lang` file the pack ships. A new `buildSrc` guard enforces the design's never-override rule mechanically and gives CI its first real coverage of this module's content.

**Tech Stack:** Java 25 (one new Java class in `mod/hytale` — see the Global Constraints override below), Gradle multi-project with `buildSrc` typed tasks, Kotlin for build logic, Hytale server `0.5.9` asset schema, JSON assets, `.lang` translation files.

## Global Constraints

- **Trunk `main` is protected.** Work on a short-lived branch cut from `main`, open a PR, and **merge — never squash, never rebase**. Delete the branch on merge.
- **Never override a shipped base-game asset.** Asset ids come from **filenames**, so a file named after a base asset silently replaces it for every player on the server. Every asset file this plan ships is prefixed `ObjectiveLine_Stormseeker_`, `Objective_Stormseeker_`, `ReachLocationMarker_Stormseeker_`, or `Furniture_Stormseeker_`.
- **Never override shipped item text.** Every lang key this plan ships is prefixed `objectives.Objective_Stormseeker_`, `objectivelines.ObjectiveLine_Stormseeker_`, `items.Furniture_Stormseeker_`, or `stormseeker.`.
- **Read verdict lines by name, never by exit status or line position.** `grep CENSUS_VERDICT`, not `tail -1`. Never pipe or chain a gate command.
- **Add no Java to `mod/hytale`.** That module compiles nothing on any machine without the proprietary game jar (CI included), so Java added there is invisible to CI. The guard added by this plan lives in `buildSrc` and reads **files**, so it runs everywhere — the same reasoning as `ManifestEntrypointCheck`.
  **Overridden mid-branch, 2026-08-25.** The play test proved this constraint uncompilable as stated: `StartObjective` is item-only and NPEs — disconnecting the player — when fired from a block interaction, and starting the line from an inscription block is exactly what this plan needs. There is no JSON-only way around an engine constraint. One class, `StormseekerStartLineInteraction`, was added to `mod/hytale` to reuse the shipped interaction's `Setup` field while skipping the item-stamping steps that don't apply without a held item. It stays invisible to CI for the same reason the rest of the module is — no game jar on the runner — same as everything else this constraint was written to keep out; it is a deliberate, evidenced exception, not a silent violation. See `docs/integration/hytale-asset-packs.md` §7c and §8 for the mechanism and the disconnect it was written to prevent.
- **Do not bump `gradle/actions`.** It is held at `v5.0.2` on purpose; v6 relicenses caching as a proprietary component.
- Toolchain is pinned (Temurin 25 via foojay). Do not add `org.gradle.configuration-cache=true` to `gradle.properties`.
- Asset paths in this plan are relative to `mod/hytale/src/main/resources/`.

## File Structure

| File | Responsibility |
|---|---|
| `buildSrc/src/main/kotlin/AssetPackIntegrityCheck.kt` | **Create.** Build guard: pack flag ↔ tree agreement, asset-id and lang-key prefix rules, objective referential integrity, lang-key coverage, duplicate-key detection. |
| `mod/hytale/build.gradle.kts` | **Modify.** Register `checkAssetPackIntegrity`, wire into `check`. |
| `mod/hytale/src/main/resources/manifest.json` | **Modify.** `IncludesAssetPack: false` → `true`. |
| `…/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json` | **Create.** The questline line. |
| `…/resources/Server/Objective/Objectives/Objective_Stormseeker_TheChamber.json` | **Create.** Act II objective 1 — reach the hall's centre. |
| `…/resources/Server/Objective/Objectives/Objective_Stormseeker_TheTrace.json` | **Create.** Act II objective 2 — read the three remaining inscriptions. |
| `…/resources/Server/Objective/ReachLocationMarkers/ReachLocationMarker_Stormseeker_RuinChamber.json` | **Create.** The chamber's reach radius. |
| `…/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Five.json` | **Create.** Starter inscription — carries `StartObjective`. |
| `…/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Housed.json` | **Create.** Second inscription. |
| `…/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Asked.json` | **Create.** Third inscription. |
| `…/resources/Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json` | **Create.** The statue's plinth inscription. |
| `…/resources/Server/Languages/en-US/server.lang` | **Create.** Every player-facing string in this plan. |
| `docs/integration/hytale-asset-packs.md` | **Modify.** Record the two mechanisms this plan proves. |
| `docs/stormseeker/stormseeker-canonical.md` | **Modify.** Implementation Status. |

## What this plan deliberately excludes

Recorded so a reviewer does not read these as oversights.

- **The ruin itself is not generated.** No prefab, no `WorldStructureAsset`, no worldgen contribution. The operator places the four inscription blocks and runs one command to site the chamber marker. Whether a mod pack can contribute worldgen structures at all is **unproven**, and the ruin's placement collides with the open **Zone-2 anchoring** question (thunder weather exists only in Zone 2 of 87 shipped weather definitions). Both belong to a later plan.
- **Act I's trail is not built**, so it is not the route in. The spec's own text makes this safe: the ruin "is **not** quest-gated. Any player may find and explore it." When Act I lands, its trail becomes a second route to the same place and nothing here changes.
- **No reward.** Act II's payoff is knowledge. No `Completions` block is authored.
- **No `NextObjectiveLineIds`.** Act III does not exist; the line ends after Act II.
- **The inscriptions say the same thing regardless of progress.** The spec says the ruin "is **not** quest-gated… What it *says* is what changes with progress." The first half ships; the second does not. The mechanism exists and is named here so the next person does not have to find it again: `SendMessageInteraction.Rules` gates whether an interaction runs, and `CanStartObjectiveRequirement` tests objective state, so a block can carry two `SendMessage` interactions selected by progress. Neither has been exercised at runtime, and Act II reads the same on a first and second visit without it. Worth doing once there is a later act for the text to change *into*.

---

### Task 1: The asset pack goes live, with a guard that keeps it honest

**Files:**
- Create: `buildSrc/src/main/kotlin/AssetPackIntegrityCheck.kt`
- Modify: `mod/hytale/build.gradle.kts`
- Modify: `mod/hytale/src/main/resources/manifest.json`
- Create: `mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json`
- Create: `mod/hytale/src/main/resources/Server/Objective/Objectives/Objective_Stormseeker_TheChamber.json`
- Create: `mod/hytale/src/main/resources/Server/Objective/ReachLocationMarkers/ReachLocationMarker_Stormseeker_RuinChamber.json`
- Create: `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`

**Interfaces:**
- Consumes: nothing.
- Produces: the Gradle task `:mod:hytale:checkAssetPackIntegrity`, wired into `check`. The asset id `ObjectiveLine_Stormseeker_TheSixthCircle`, which Task 3 starts and Task 4 extends. The lang file `Server/Languages/en-US/server.lang`, which every later task appends to. The guard's approved prefix lists `ASSET_ID_PREFIXES` and `LANG_KEY_PREFIXES`, which Task 2 relies on already containing `Furniture_Stormseeker_` and `items.Furniture_Stormseeker_`.

**Why a guard, and why in `buildSrc`.** The asset tree is invisible to the compiler in exactly the way `manifest.json`'s `Main` string is. A file named `ObjectiveLine_Test.json` would silently replace the base game's asset for every player on the server, the build would stay green, every test would pass, and the first signal would be a player noticing the game changed. `mod/hytale` compiles nothing on CI, so a JUnit test in that module would be vacuous where it matters most; a `buildSrc` task reading files runs everywhere.

- [ ] **Step 1: Create the branch**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git checkout main && git pull
git checkout -b feat/stormseeker-act-ii-the-trace
```

- [ ] **Step 2: Write the guard**

Create `buildSrc/src/main/kotlin/AssetPackIntegrityCheck.kt`:

```kotlin
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
```

- [ ] **Step 3: Wire the guard into `check`**

In `mod/hytale/build.gradle.kts`, immediately after the `checkManifestEntrypoint` registration block, add:

```kotlin
// The asset tree is invisible to the compiler in the same way the manifest entrypoint is,
// and a mis-named file silently OVERRIDES a base-game asset rather than failing. Reads
// files, not classes, so this is meaningful on CI where the module compiles nothing.
val checkAssetPackIntegrity =
    tasks.register<AssetPackIntegrityCheck>("checkAssetPackIntegrity") {
        group = "verification"
        description =
            "Verifies this module's shipped asset pack cannot override base-game assets or " +
            "text, that objective lines name objectives that exist, and that every " +
            "translation key an asset references is defined."
        manifest.set(layout.projectDirectory.file("src/main/resources/manifest.json"))
        resourceRoot.set(layout.projectDirectory.dir("src/main/resources"))
        outputs.upToDateWhen { false }
    }
```

Then extend the existing `check` wiring. Replace this line:

```kotlin
tasks.named("check") { dependsOn(checkManifestEntrypoint) }
```

with:

```kotlin
tasks.named("check") { dependsOn(checkManifestEntrypoint, checkAssetPackIntegrity) }
```

- [ ] **Step 4: Create the asset tree, leaving `IncludesAssetPack` false**

This is the red half of the cycle: the assets exist, the flag does not agree with them.

`mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json`:

```json
{
  "Category": "Stormseeker",
  "TitleId": "server.objectivelines.ObjectiveLine_Stormseeker_TheSixthCircle.title",
  "DescriptionId": "server.objectivelines.ObjectiveLine_Stormseeker_TheSixthCircle.desc",
  "ObjectiveIds": [
    "Objective_Stormseeker_TheChamber"
  ]
}
```

`mod/hytale/src/main/resources/Server/Objective/Objectives/Objective_Stormseeker_TheChamber.json`:

```json
{
  "Category": "Stormseeker",
  "TitleId": "server.objectives.Objective_Stormseeker_TheChamber.title",
  "DescriptionId": "server.objectives.Objective_Stormseeker_TheChamber.desc",
  "TaskSets": [
    {
      "Tasks": [
        {
          "Type": "ReachLocation",
          "TargetLocation": "ReachLocationMarker_Stormseeker_RuinChamber"
        }
      ]
    }
  ]
}
```

`mod/hytale/src/main/resources/Server/Objective/ReachLocationMarkers/ReachLocationMarker_Stormseeker_RuinChamber.json`:

```json
{
  "Radius": 6,
  "Name": "The Hall"
}
```

`mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`:

```
# Stormseeker — Act II: The Trace
#
# The filename supplies the 'server.' prefix: a key written here as
# 'objectives.X.title' resolves as 'server.objectives.X.title'.
#
# Every key in this file MUST stay inside a Stormseeker-scoped prefix. A key that
# collides with a base-game key replaces that text for every player on the server,
# including players who never touch this questline. checkAssetPackIntegrity enforces this.

# === the line ===

objectivelines.ObjectiveLine_Stormseeker_TheSixthCircle.title = The Sixth Circle
objectivelines.ObjectiveLine_Stormseeker_TheSixthCircle.desc = Someone built here, and stopped.

# === Act II, objective 1 — the chamber ===

objectives.Objective_Stormseeker_TheChamber.title = Deeper In
objectives.Objective_Stormseeker_TheChamber.desc = The hall was built to hold something. Find where it was meant to stand.
objectives.Objective_Stormseeker_TheChamber.taskSet.0.task.0 = Reach the centre of the hall
```

- [ ] **Step 5: Run the guard and verify it fails for the right reason**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**, with this line in the output:

```
- Server/ asset tree exists but manifest.json declares "IncludesAssetPack": false, so the server will never load these assets.
```

If it fails for any other reason, fix that first — the point of this step is that the guard's red is the red you expect.

- [ ] **Step 6: Turn the pack on**

In `mod/hytale/src/main/resources/manifest.json`, change:

```json
    "IncludesAssetPack": false,
```

to:

```json
    "IncludesAssetPack": true,
```

- [ ] **Step 7: Run the guard and verify it passes**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Prove the guard catches an override, then undo it**

This is the negative control. Without it the guard is untested and could be passing vacuously.

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
cp mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json \
   mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Test.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**, naming the offending id:

```
- asset id 'ObjectiveLine_Test' (Server/Objective/ObjectiveLines/ObjectiveLine_Test.json) does not start with one of [...]
```

`ObjectiveLine_Test` is a real base-game asset, so this is the exact failure the guard exists to prevent. Now remove it:

```bash
rm mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Test.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Run the full build**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`. Spotless formats `buildSrc` Kotlin; if it reports a violation, run `./gradlew spotlessApply` and re-run `build`.

- [ ] **Step 10: Commit**

```bash
git add buildSrc/src/main/kotlin/AssetPackIntegrityCheck.kt \
        mod/hytale/build.gradle.kts \
        mod/hytale/src/main/resources/manifest.json \
        mod/hytale/src/main/resources/Server
git commit -m "feat(mod): ship an asset pack, guarded against overriding the base game

Flips IncludesAssetPack to true and adds the Stormseeker objective line with
its first objective, authored as native Hytale assets.

Adds AssetPackIntegrityCheck, wired into check. Asset ids come from filenames
and lang keys are global, so a mis-named file or key silently OVERRIDES the
base game while the build stays green. The guard requires a Stormseeker-scoped
prefix on both, which no base-game id uses, so it needs no copy of the base
asset list. It reads files rather than classes, so unlike everything else in
this module it is meaningful on CI."
```

---

### Task 2: The four inscription blocks

**Files:**
- Create: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Five.json`
- Create: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Housed.json`
- Create: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Asked.json`
- Create: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json`
- Modify: `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`

**Interfaces:**
- Consumes: from Task 1, the guard's `Furniture_Stormseeker_` asset-id prefix and `items.Furniture_Stormseeker_` lang-key prefix, and the `server.lang` file.
- Produces: four block ids — `Furniture_Stormseeker_Inscription_Five`, `Furniture_Stormseeker_Inscription_Housed`, `Furniture_Stormseeker_Inscription_Asked`, `Furniture_Stormseeker_Statue_Silent`. Task 3 attaches interactions to all four; Task 4 targets the last three with `UseBlock` tasks.

**Why our own blocks rather than the shipped ones.** The spec places the ruin in the `Temple_Wind` palette and its statue is `Furniture_Temple_Wind_Statue_Gaia`. Attaching an interaction to those means shipping files with those names — which **replaces them for every player on the server**, exactly what the governing rule forbids. So these are new ids that reuse the shipped models and textures. Cross-pack asset references resolve (proven 2026-08-25), so pointing `CustomModel` at a base `Common/` path costs nothing and the blocks look identical to the palette around them.

These blocks have no `Recipe` and no `ResourceTypes`: they are placed by whoever builds the ruin, not crafted or gathered.

- [ ] **Step 1: Create the first inscription**

`Furniture_Stormseeker_Inscription_Five.json`. Modelled on the shipped `Furniture_Temple_Wind_Sign`, minus the recipe:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Five.name"
  },
  "PlayerAnimationsId": "Block",
  "Categories": [
    "Furniture.Signs"
  ],
  "Set": "Furniture_Temple_Wind",
  "BlockType": {
    "BlockParticleSetId": "Stone",
    "BlockSoundSetId": "Stone",
    "PhysicalMaterialId": "Stone",
    "CustomModel": "Blocks/Decorative_Sets/Temple_Wind/Sign.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Decorative_Sets/Temple_Wind/Sign_Texture.png",
        "Weight": 1
      }
    ],
    "DrawType": "Model",
    "Gathering": {
      "Soft": {
        "IsWeaponBreakable": false
      }
    },
    "HitboxType": "Sign_Wall",
    "Material": "Solid",
    "Opacity": "Transparent",
    "ParticleColor": "#cca159",
    "Support": {
      "East": [
        {
          "FaceType": "Full"
        }
      ],
      "West": [
        {
          "FaceType": "Full"
        }
      ]
    },
    "VariantRotation": "NESW",
    "TextureComputedColor": "#B38440"
  },
  "IconProperties": {
    "Scale": 0.58823,
    "Rotation": [
      22.5,
      45,
      22.5
    ],
    "Translation": [
      8.39,
      -19.21
    ]
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Temple_Wind_Sign.png",
  "Tags": {
    "Type": [
      "Furniture"
    ],
    "Family": [
      "Temple"
    ]
  },
  "ItemSoundSetId": "ISS_Blocks_Stone"
}
```

- [ ] **Step 2: Create the second and third inscriptions**

`Furniture_Stormseeker_Inscription_Housed.json` — identical to Step 1 except the `Name` key:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Housed.name"
  },
  "PlayerAnimationsId": "Block",
  "Categories": [
    "Furniture.Signs"
  ],
  "Set": "Furniture_Temple_Wind",
  "BlockType": {
    "BlockParticleSetId": "Stone",
    "BlockSoundSetId": "Stone",
    "PhysicalMaterialId": "Stone",
    "CustomModel": "Blocks/Decorative_Sets/Temple_Wind/Sign.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Decorative_Sets/Temple_Wind/Sign_Texture.png",
        "Weight": 1
      }
    ],
    "DrawType": "Model",
    "Gathering": {
      "Soft": {
        "IsWeaponBreakable": false
      }
    },
    "HitboxType": "Sign_Wall",
    "Material": "Solid",
    "Opacity": "Transparent",
    "ParticleColor": "#cca159",
    "Support": {
      "East": [
        {
          "FaceType": "Full"
        }
      ],
      "West": [
        {
          "FaceType": "Full"
        }
      ]
    },
    "VariantRotation": "NESW",
    "TextureComputedColor": "#B38440"
  },
  "IconProperties": {
    "Scale": 0.58823,
    "Rotation": [
      22.5,
      45,
      22.5
    ],
    "Translation": [
      8.39,
      -19.21
    ]
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Temple_Wind_Sign.png",
  "Tags": {
    "Type": [
      "Furniture"
    ],
    "Family": [
      "Temple"
    ]
  },
  "ItemSoundSetId": "ISS_Blocks_Stone"
}
```

`Furniture_Stormseeker_Inscription_Asked.json`:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Asked.name"
  },
  "PlayerAnimationsId": "Block",
  "Categories": [
    "Furniture.Signs"
  ],
  "Set": "Furniture_Temple_Wind",
  "BlockType": {
    "BlockParticleSetId": "Stone",
    "BlockSoundSetId": "Stone",
    "PhysicalMaterialId": "Stone",
    "CustomModel": "Blocks/Decorative_Sets/Temple_Wind/Sign.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Decorative_Sets/Temple_Wind/Sign_Texture.png",
        "Weight": 1
      }
    ],
    "DrawType": "Model",
    "Gathering": {
      "Soft": {
        "IsWeaponBreakable": false
      }
    },
    "HitboxType": "Sign_Wall",
    "Material": "Solid",
    "Opacity": "Transparent",
    "ParticleColor": "#cca159",
    "Support": {
      "East": [
        {
          "FaceType": "Full"
        }
      ],
      "West": [
        {
          "FaceType": "Full"
        }
      ]
    },
    "VariantRotation": "NESW",
    "TextureComputedColor": "#B38440"
  },
  "IconProperties": {
    "Scale": 0.58823,
    "Rotation": [
      22.5,
      45,
      22.5
    ],
    "Translation": [
      8.39,
      -19.21
    ]
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Temple_Wind_Sign.png",
  "Tags": {
    "Type": [
      "Furniture"
    ],
    "Family": [
      "Temple"
    ]
  },
  "ItemSoundSetId": "ISS_Blocks_Stone"
}
```

- [ ] **Step 3: Create the statue**

`Furniture_Stormseeker_Statue_Silent.json`. Modelled on the shipped `Furniture_Temple_Wind_Statue_Gaia`:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Statue_Silent.name"
  },
  "PlayerAnimationsId": "Block",
  "Categories": [
    "Blocks.Deco"
  ],
  "Set": "Furniture_Temple_Light",
  "IconProperties": {
    "Rotation": [
      22.5,
      45,
      22.5
    ],
    "Scale": 0.29,
    "Translation": [
      0,
      -36.9
    ]
  },
  "BlockType": {
    "BlockParticleSetId": "Stone",
    "BlockSoundSetId": "Stone",
    "PhysicalMaterialId": "Stone",
    "CustomModel": "Blocks/Decorative_Sets/Temple_Light/Gaia_Statue_01.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Decorative_Sets/Temple_Light/Gaia_Statue_01_Textures/Sandstone.png",
        "Weight": 1
      }
    ],
    "DrawType": "Model",
    "Flags": {},
    "Gathering": {
      "Breaking": {
        "GatherType": "Rocks"
      }
    },
    "HitboxType": "Statue",
    "Material": "Solid",
    "Opacity": "Transparent",
    "Support": {
      "Down": [
        {
          "FaceType": "Full"
        }
      ]
    },
    "VariantRotation": "NESW",
    "ParticleColor": "#eba936",
    "TextureComputedColor": "#CBA663"
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Temple_Wind_Statue_Gaia.png",
  "Tags": {
    "Type": [
      "Furniture"
    ],
    "Family": [
      "Temple"
    ]
  },
  "Scale": 0.6,
  "ItemSoundSetId": "ISS_Blocks_Stone",
  "MaxStack": 1
}
```

- [ ] **Step 4: Run the guard and verify it fails for the right reason**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**, with four lines of this shape:

```
- Server/Item/Items/Furniture_Stormseeker_Inscription_Five.json references translation key 'server.items.Furniture_Stormseeker_Inscription_Five.name', which is not defined in Server/Languages/en-US/server.lang — it would render to the player as the raw key.
```

This is the guard doing its job unprompted: four new blocks, no names, and it says so before a player ever sees a raw key.

- [ ] **Step 5: Name the blocks**

Append to `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`:

```

# === Act II block names ===

items.Furniture_Stormseeker_Inscription_Five.name = Inscription
items.Furniture_Stormseeker_Inscription_Housed.name = Inscription
items.Furniture_Stormseeker_Inscription_Asked.name = Inscription
items.Furniture_Stormseeker_Statue_Silent.name = Statue of a Silent Deity
```

The three wall inscriptions share a name on purpose. The player is not meant to be able to tell them apart before reading them, and a builder placing them works from the file names, not the tooltip.

- [ ] **Step 6: Run the guard and verify it passes**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add mod/hytale/src/main/resources/Server
git commit -m "feat(mod): add the four Act II inscription blocks

Four new block ids reusing the shipped Temple_Wind sign and Gaia statue models.
They are new ids rather than files named after the shipped blocks, because a
file named Furniture_Temple_Wind_Sign.json would replace that block for every
player on the server. No recipe: these are placed by whoever builds the ruin.

The integrity guard caught all four missing name keys before they could reach
a player as raw keys."
```

---

### Task 3: The inscriptions speak, and the first one starts the questline

**Files:**
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Five.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Housed.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Asked.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json`
- Modify: `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`

**Interfaces:**
- Consumes: the four block ids from Task 2; `ObjectiveLine_Stormseeker_TheSixthCircle` from Task 1.
- Produces: four `stormseeker.inscription.*` translation keys, and the questline's entry point. After this task, right-clicking `Furniture_Stormseeker_Inscription_Five` starts the line.

**The pattern.** The shipped `Test_Objective_Line_Interaction` item is the reference: a block whose `Interactions.Secondary` inlines a `RootInteraction` whose `Interactions` array holds a `StartObjective` with `Setup: {"Type": "ObjectiveLine", ...}`. `SendMessageInteraction.Next` is a **single** interaction, not an array, and its description is "The interactions to run when this interaction succeeds" — so the chain is `SendMessage` → `Next` → `StartObjective`, which is unambiguous about ordering in a way a two-element array would not be.

Text is delivered by `Key`, not by the literal `Message` field, so every player-facing string in this questline stays in one file that the guard checks. Task 5 verifies the key actually resolves; if it renders raw, the fallback is recorded there.

- [ ] **Step 1: Add the four inscription texts**

Append to `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`:

```

# === Act II inscriptions — the builders' own record ===
#
# From stormseeker-canonical.md v4.0, Act II "Delivery". The first line is
# literally true in the shipped game: Earth, Fire, Frost, Poison and Sand all
# have an Elemental Circle. Storm does not.

stormseeker.inscription.five = We raised five. The sixth would not stand.
stormseeker.inscription.housed = The others gather where we set the stones. This one would not be housed.
stormseeker.inscription.asked = It answered once. We asked again.
stormseeker.inscription.silent = We carved it listening. It has not spoken since.
```

- [ ] **Step 2: Make the first inscription speak and start the line**

In `Furniture_Stormseeker_Inscription_Five.json`, add an `Interactions` block immediately after the `"Set"` line:

```json
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.five",
          "Next": {
            "Type": "StartObjective",
            "Setup": {
              "Type": "ObjectiveLine",
              "ObjectiveLineId": "ObjectiveLine_Stormseeker_TheSixthCircle"
            }
          }
        }
      ]
    }
  },
```

- [ ] **Step 3: Make the other three speak**

In `Furniture_Stormseeker_Inscription_Housed.json`, after `"Set"`:

```json
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.housed"
        }
      ]
    }
  },
```

In `Furniture_Stormseeker_Inscription_Asked.json`, after `"Set"`:

```json
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.asked"
        }
      ]
    }
  },
```

In `Furniture_Stormseeker_Statue_Silent.json`, after `"Set"`:

```json
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.silent"
        }
      ]
    }
  },
```

None of the other three carries `StartObjective`. Only the first inscription begins the questline; the rest are tasks within it, and Task 4 makes them count.

- [ ] **Step 4: Run the guard and verify it passes**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`. The guard reads `Key` fields at any nesting depth, so a typo in any of the four keys fails here rather than showing a player a raw string.

- [ ] **Step 5: Prove that key-checking actually reaches nested interactions**

Negative control for the `collectKeys` recursion, which is the one part of the guard that is not exercised by the happy path.

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
sed -i 's/server.stormseeker.inscription.silent/server.stormseeker.inscription.slient/' \
  mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**, naming the typo:

```
- Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json references translation key 'server.stormseeker.inscription.slient', which is not defined ...
```

Now undo it:

```bash
sed -i 's/server.stormseeker.inscription.slient/server.stormseeker.inscription.silent/' \
  mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Statue_Silent.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add mod/hytale/src/main/resources/Server
git commit -m "feat(mod): the inscriptions speak, and the first one starts the line

SendMessage carries the builders' four lines; the first chains Next into
StartObjective, so reading a wall is what begins the questline. That keeps
Act II's entry inside the fiction and needs no Act I trail, which does not
exist yet — the ruin is deliberately not quest-gated, so discovery is the
entry.

Text is delivered by lang Key rather than a literal Message so that every
player-facing string stays in one guarded file."
```

---

### Task 4: Reading the record completes the act

**Files:**
- Create: `mod/hytale/src/main/resources/Server/Objective/Objectives/Objective_Stormseeker_TheTrace.json`
- Modify: `mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json`
- Modify: `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`

**Interfaces:**
- Consumes: `Furniture_Stormseeker_Inscription_Housed`, `Furniture_Stormseeker_Inscription_Asked`, `Furniture_Stormseeker_Statue_Silent` from Task 2; `ObjectiveLine_Stormseeker_TheSixthCircle` from Task 1.
- Produces: `Objective_Stormseeker_TheTrace`, the line's second and final objective. After this task the line completes.

**Task text is a delivery channel, not instructions.** `objectives.<AssetId>.taskSet.<i>.task.<j>` is the per-task string, which is how the base game writes its own (`objectives.Objective_Gather.taskSet.0.task.0 = Gather <item is="Soil_Dirt"/>`). Act IV's delivery rule — *"Instead of: Reach the location / Write: Follow what the storm burned"* — applies here too, one act early, because this is the surface it names.

Three separate `UseBlock` tasks rather than one with `Count: 3`, because `Count` counts uses and would be satisfied by right-clicking a single inscription three times. Separate tasks also give each line its own string.

- [ ] **Step 1: Create the objective**

`mod/hytale/src/main/resources/Server/Objective/Objectives/Objective_Stormseeker_TheTrace.json`:

```json
{
  "Category": "Stormseeker",
  "TitleId": "server.objectives.Objective_Stormseeker_TheTrace.title",
  "DescriptionId": "server.objectives.Objective_Stormseeker_TheTrace.desc",
  "TaskSets": [
    {
      "Tasks": [
        {
          "Type": "UseBlock",
          "BlockTagOrItemId": {
            "ItemId": "Furniture_Stormseeker_Inscription_Housed"
          },
          "Count": 1
        },
        {
          "Type": "UseBlock",
          "BlockTagOrItemId": {
            "ItemId": "Furniture_Stormseeker_Inscription_Asked"
          },
          "Count": 1
        },
        {
          "Type": "UseBlock",
          "BlockTagOrItemId": {
            "ItemId": "Furniture_Stormseeker_Statue_Silent"
          },
          "Count": 1
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: Add it to the line**

In `ObjectiveLine_Stormseeker_TheSixthCircle.json`, change:

```json
  "ObjectiveIds": [
    "Objective_Stormseeker_TheChamber"
  ]
```

to:

```json
  "ObjectiveIds": [
    "Objective_Stormseeker_TheChamber",
    "Objective_Stormseeker_TheTrace"
  ]
```

- [ ] **Step 3: Run the guard and verify it fails for the right reason**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**, with **two** lines — one for `.title`, one for `.desc`:

```
- Server/Objective/Objectives/Objective_Stormseeker_TheTrace.json references translation key 'server.objectives.Objective_Stormseeker_TheTrace.title', which is not defined ...
```

Two, not three: the guard finds keys by walking the JSON for `TitleId` / `DescriptionId` / `Key` / `server.`-prefixed `Name` fields, and the **per-task** text keys appear nowhere in the JSON at all — the engine derives `objectives.<AssetId>.taskSet.<i>.task.<j>` by naming convention from the asset id and the task's array index. There is no node for the guard to walk into, which is the same blindness Task 4's preamble warns about from the other direction: nothing in the build can check that task index 2's text describes the block at index 2.

- [ ] **Step 4: Write the objective's text**

Append to `mod/hytale/src/main/resources/Server/Languages/en-US/server.lang`:

```

# === Act II, objective 2 — the record ===
#
# In-fiction, never instructional — the delivery rule from
# stormseeker-canonical.md "Lore Delivery". These are the player's own
# attention, not a quest-giver's voice.

objectives.Objective_Stormseeker_TheTrace.title = What They Wrote Down
objectives.Objective_Stormseeker_TheTrace.desc = They left a record. It is not an apology.
objectives.Objective_Stormseeker_TheTrace.taskSet.0.task.0 = Read the wall beside the stones
objectives.Objective_Stormseeker_TheTrace.taskSet.0.task.1 = Read the wall they came back to
objectives.Objective_Stormseeker_TheTrace.taskSet.0.task.2 = Read the statue's plinth
```

- [ ] **Step 5: Run the guard and verify it passes**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Prove the referential-integrity rule fires, then undo it**

Negative control for the objective-line rule, which nothing else has exercised.

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
sed -i 's/Objective_Stormseeker_TheTrace"/Objective_Stormseeker_TheTraces"/' \
  mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: **FAIL**:

```
- ObjectiveLine_Stormseeker_TheSixthCircle.json names objective 'Objective_Stormseeker_TheTraces', which has no file at Server/Objective/Objectives/Objective_Stormseeker_TheTraces.json
```

Undo it:

```bash
sed -i 's/Objective_Stormseeker_TheTraces"/Objective_Stormseeker_TheTrace"/' \
  mod/hytale/src/main/resources/Server/Objective/ObjectiveLines/ObjectiveLine_Stormseeker_TheSixthCircle.json
```

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the full build and both censuses**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`.

Run each of the following **bare** — nothing piped, chained, or appended — then read the named verdict line out of the output:

```bash
python3 scripts/test-census.py
```

```bash
python3 scripts/coverage-census.py
```

Expected: a `CENSUS_VERDICT` line and a `COVERAGE_VERDICT` line, both reporting pass. An **absent** verdict line is a failure, not a pass.

- [ ] **Step 8: Commit**

```bash
git add mod/hytale/src/main/resources/Server
git commit -m "feat(mod): Act II completes when the builders' record is read

Three UseBlock tasks, one per remaining inscription, rather than one task with
Count 3 — Count counts uses, so a single inscription clicked three times would
satisfy it.

Task text uses the in-fiction register the delivery model calls for; the
per-task lang key is the objective-text channel Act IV depends on, exercised
here one act early."
```

---

### Task 5: Verify it on a real server

**Files:**
- Create: `.scratch/act-ii-verify/README.md` (git-ignored; notes only)
- Modify: whichever asset files the verification proves wrong

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: an empirical answer to the two things the build cannot check — whether a mod pack's `server.lang` is actually loaded, and whether `SendMessageInteraction.Key` resolves for the player.

**Why this task is not optional.** The build guard proves the assets are *internally* consistent. It cannot prove the server loads them, that a pack's translations reach a client, or that a `UseBlock` task fires on a block we invented. The last session's recorded lesson is exact about this: a harness was handed over after verifying the server *booted and bound its port*, and it could not work, because the thing it existed to do happened on the far side of a client login. **Verify at the boundary the artifact exists to cross.** For this plan that boundary is a connected player right-clicking a block.

Two specific uncertainties this settles:

1. **Pack-shipped translations.** `I18nModule` calls `AssetModule.getAssetPacks()` → `forEach` → `loadMessagesFromPack(pack)`, and that method resolves `pack.getRoot()/Server/Languages`. That is bytecode, not a run. Nothing has ever shipped a `.lang` from a mod pack here.
2. **The objective-line key prefix.** `ObjectiveAsset`'s editor template is `server.objectives.{assetId}.title`, but `ObjectiveLineAsset`'s is `objectivelines.{assetId}.title` — **without** the `server.` prefix. Since the prefix comes from the lang **filename**, those two cannot both be right for a file named `server.lang`. The fallback, if the line title renders raw, is a second file `Server/Languages/en-US/objectivelines.lang` containing `ObjectiveLine_Stormseeker_TheSixthCircle.title = The Sixth Circle` and the `TitleId`/`DescriptionId` fields shortened to drop `server.`.

- [ ] **Step 1: Build the mod jar**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew :mod:hytale:shadowJar
```

Expected: `BUILD SUCCESSFUL`. Find the jar:

```bash
ls -la mod/hytale/build/libs/
```

- [ ] **Step 2: Confirm the assets are actually inside the jar**

A resource that never reached the jar is the cheapest possible failure and is invisible from the source tree.

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
unzip -l mod/hytale/build/libs/*-all.jar | grep -E 'Server/(Objective|Item|Languages)'
```

Expected: ten `Server/...` entries — one line, one marker, two objectives, four items, one `server.lang`. If the `Server/` tree is missing, the shadow jar is not carrying `src/main/resources`; fix that before going further.

- [ ] **Step 3: Stand up a server with the mod**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
mkdir -p .scratch/act-ii-verify/mods
cp mod/hytale/build/libs/*-all.jar .scratch/act-ii-verify/mods/
GAME=~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest
cd .scratch/act-ii-verify
java -jar "$GAME/Server/HytaleServer.jar" --assets "$GAME/Assets.zip" --mods ./mods --disable-sentry --disable-file-watcher
```

**Do not pass `--auth-mode offline`.** It is a documented value of that flag's enum, the server accepts it, boots clean and binds the port — and then refuses the client at login with *"offline mode is only valid in singleplayer"*. A multiplayer server must authenticate itself. At the server console run:

```
/auth login device
```

and complete the device flow it prints.

- [ ] **Step 4: Read the boot log for asset and translation loading**

In the server output, confirm all three:

```
[AssetModule] Loaded pack: io.github.legendaryforge:LegendaryHytale from ...
```

```
Loaded %d entries for 'en-US' from ...
```

and the **absence** of any line of this shape:

```
[AssetStore|ObjectiveLineAsset] Failed to validate asset: ...
```

A validation failure names the offending asset and key. Read the `[AssetStore|...]` lines by name; do not judge this by the exit code.

If no `Loaded ... entries for 'en-US'` line mentions our pack, uncertainty (1) has resolved negatively — pack translations are not loaded. In that case, switch all four inscriptions from `"Key"` to `"Message"` with the literal text, note it in the README from Step 8, and continue; the objective `TitleId`/`DescriptionId` fields have no literal alternative and would need their own follow-up.

- [ ] **Step 5: Build a minimal ruin and site the marker**

Connect a client to the server. In creative mode, place the four blocks a few metres apart, with the statue at the centre:

```
/give Furniture_Stormseeker_Inscription_Five 1
/give Furniture_Stormseeker_Inscription_Housed 1
/give Furniture_Stormseeker_Inscription_Asked 1
/give Furniture_Stormseeker_Statue_Silent 1
```

Stand at the centre of the arrangement and site the chamber marker:

```
/objective reachLocationMarker add ReachLocationMarker_Stormseeker_RuinChamber
```

Expected chat response: `ReachLocationMarker 'ReachLocationMarker_Stormseeker_RuinChamber' added!`

If instead you get `ReachLocationMarker '...' not found!`, the marker asset did not load — go back to Step 4.

- [ ] **Step 6: Play Act II end to end**

Walk away from the arrangement far enough to leave the 6-block radius, then:

1. **Right-click `Furniture_Stormseeker_Inscription_Five`.**
   Expected: the message *"We raised five. The sixth would not stand."* appears, **as prose, not as a raw key**, and the objective panel shows **The Sixth Circle** with the objective **Deeper In**.
   If the message appears as `server.stormseeker.inscription.five`, uncertainty (1) resolved negatively — apply the `Message` fallback from Step 4.
   If the line's title shows as `server.objectivelines....title`, apply the `objectivelines.lang` fallback described in this task's preamble.
2. **Walk to the centre of the arrangement.**
   Expected: **Deeper In** completes and **What They Wrote Down** becomes active, showing three tasks in the wording from Task 4.
3. **Right-click the second inscription, the third, and the statue.**
   Expected: each shows its line of prose, and its matching task ticks off. After the third, the objective completes.
4. **Confirm line-level completion:**

```
/objective history
```

Expected: `ObjectiveLine_Stormseeker_TheSixthCircle` with `TimesCompleted` of **1**.

Note the trap here: `/objective complete objective <id>` completes an objective but does **not** roll up to line completion — `TimesCompleted` stays `0`. Only a genuine play-through sets it. Do not shortcut steps 1–3 with admin commands and then read this as a pass.

5. **Confirm persistence.** Disconnect, then:

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
cat .scratch/act-ii-verify/universe/players/*.json | python3 -m json.tool | grep -A20 ObjectiveHistory
```

Expected: our line id present, both objectives nested under it with completion counts and timestamps.

- [ ] **Step 7: Settle whether `Parent` inheritance works**

The server is already running; this is one extra asset and one extra look.

Every asset schema carries a `Parent` field — *"this asset will inherit properties from the named asset… in the case of nested structures this will apply to the fields within the structure."* It has never been exercised here. It matters well beyond this branch: Act IV's Circle raises **5–7 tiers** that differ by a tier number, the materials share a backbone, and questline #2 is by design "JSON plus a couple of registrations" — the whole set again, per element. If `Parent` works, near-identical asset files stop being the default shape of every questline. If it does not, that is worth knowing once rather than rediscovering per act.

Create a throwaway probe asset `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_ParentProbe.json` that inherits everything from an existing inscription and overrides only its name:

```json
{
  "Parent": "Furniture_Stormseeker_Inscription_Housed",
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Five.name"
  }
}
```

It reuses an existing name key on purpose, so no lang entry is needed and the integrity guard stays green.

Rebuild the jar, restart the server, and check three things in order:

1. **It loads.** No `[AssetStore|...] Failed to validate asset: Furniture_Stormseeker_Inscription_ParentProbe` line at boot.
2. **The inherited half arrived.** `/give Furniture_Stormseeker_Inscription_ParentProbe 1`, place it — it must render as the Temple_Wind sign, not as a missing model.
3. **The inherited *nested* half arrived.** Right-click it. If the inherited `Interactions` block came through, it prints *"The others gather where we set the stones. This one would not be housed."* **This is the load-bearing observation** — inheriting a flat scalar proves little; inheriting a nested interaction chain is what the tier assets and the elemental family would actually depend on.

Record all three answers. Then delete the probe:

```bash
rm mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_ParentProbe.json
```

- [ ] **Step 8: Fix whatever the play-through found**

Apply any fallback the previous steps triggered, re-run `./gradlew :mod:hytale:checkAssetPackIntegrity` and `./gradlew build`, rebuild the jar, and repeat Step 6 until the play-through is clean. Do not proceed on a partial pass.

- [ ] **Step 9: Write down what the run actually established**

Create `.scratch/act-ii-verify/README.md` recording, in plain terms: whether pack-shipped `server.lang` loaded; which key form the objective line needed; whether `SendMessage` `Key` resolved for the client; whether `UseBlock` fires on a mod-defined block; the `TimesCompleted` value from a real play-through; and **the three `Parent` answers from Step 7**. This directory is git-ignored — the durable version goes into `docs/` in Task 7.

- [ ] **Step 10: Commit any fixes**

```bash
git add mod/hytale/src/main/resources/Server
git commit -m "fix(mod): corrections from the Act II play-through

Found by playing the questline on a real server with a connected client rather
than by checking the server booted. See docs update for what the run
established."
```

If the play-through required no changes, skip this step — there is nothing to commit.

---

### Task 6: Retire the duplication — CONDITIONAL on Task 5 Step 7

**Run this task only if all three `Parent` checks in Task 5 Step 7 passed, including the nested `Interactions` one.** If any failed, skip the task entirely and say so in Task 7 Step 4 — the copies stand, with a recorded reason.

**Files:**
- Create: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Base.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Five.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Housed.json`
- Modify: `mod/hytale/src/main/resources/Server/Item/Items/Furniture_Stormseeker_Inscription_Asked.json`

**Interfaces:**
- Consumes: the three `Parent` answers from Task 5 Step 7.
- Produces: `Furniture_Stormseeker_Inscription_Base`, the shared definition Act III onward inherits from. No asset id changes, so nothing else in the branch is affected.

**Why this task exists.** Tasks 2–4 shipped three near-identical block files because `Parent` was unverified and this branch is built on verified mechanisms. Task 5 verifies it. Duplication that is cheap to undo is fine; duplication that becomes the house style is not — and the volume ahead makes that a real risk, not a hypothetical one: 5–7 Circle tier assets, the Class A–E materials, and a full repeat of the set for every element in the family.

The statue is deliberately **not** converted. It is a genuinely different block — different model, hitbox, gathering and scale — and folding it under a sign's base would be inheritance for its own sake.

- [ ] **Step 1: Extract the base**

Create `Furniture_Stormseeker_Inscription_Base.json` holding everything the three inscriptions share — which is everything except `TranslationProperties` and `Interactions`:

```json
{
  "PlayerAnimationsId": "Block",
  "Categories": [
    "Furniture.Signs"
  ],
  "Set": "Furniture_Temple_Wind",
  "BlockType": {
    "BlockParticleSetId": "Stone",
    "BlockSoundSetId": "Stone",
    "PhysicalMaterialId": "Stone",
    "CustomModel": "Blocks/Decorative_Sets/Temple_Wind/Sign.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Decorative_Sets/Temple_Wind/Sign_Texture.png",
        "Weight": 1
      }
    ],
    "DrawType": "Model",
    "Gathering": {
      "Soft": {
        "IsWeaponBreakable": false
      }
    },
    "HitboxType": "Sign_Wall",
    "Material": "Solid",
    "Opacity": "Transparent",
    "ParticleColor": "#cca159",
    "Support": {
      "East": [
        {
          "FaceType": "Full"
        }
      ],
      "West": [
        {
          "FaceType": "Full"
        }
      ]
    },
    "VariantRotation": "NESW",
    "TextureComputedColor": "#B38440"
  },
  "IconProperties": {
    "Scale": 0.58823,
    "Rotation": [
      22.5,
      45,
      22.5
    ],
    "Translation": [
      8.39,
      -19.21
    ]
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Temple_Wind_Sign.png",
  "Tags": {
    "Type": [
      "Furniture"
    ],
    "Family": [
      "Temple"
    ]
  },
  "ItemSoundSetId": "ISS_Blocks_Stone"
}
```

- [ ] **Step 2: Reduce the three inscriptions to what differs**

Replace the entire contents of `Furniture_Stormseeker_Inscription_Five.json` with:

```json
{
  "Parent": "Furniture_Stormseeker_Inscription_Base",
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Five.name"
  },
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.five",
          "Next": {
            "Type": "StartObjective",
            "Setup": {
              "Type": "ObjectiveLine",
              "ObjectiveLineId": "ObjectiveLine_Stormseeker_TheSixthCircle"
            }
          }
        }
      ]
    }
  }
}
```

Replace the entire contents of `Furniture_Stormseeker_Inscription_Housed.json` with:

```json
{
  "Parent": "Furniture_Stormseeker_Inscription_Base",
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Housed.name"
  },
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.housed"
        }
      ]
    }
  }
}
```

Replace the entire contents of `Furniture_Stormseeker_Inscription_Asked.json` with:

```json
{
  "Parent": "Furniture_Stormseeker_Inscription_Base",
  "TranslationProperties": {
    "Name": "server.items.Furniture_Stormseeker_Inscription_Asked.name"
  },
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Key": "server.stormseeker.inscription.asked"
        }
      ]
    }
  }
}
```

- [ ] **Step 3: Run the guard**

Run: `./gradlew :mod:hytale:checkAssetPackIntegrity`

Expected: `BUILD SUCCESSFUL`. The base asset carries no translation key of its own, and the three children keep the keys they already had, so nothing about the guard's expectations changes.

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Re-play the three inscriptions on the server**

The guard checks files, not the engine's inheritance. Rebuild the jar, restart the server, and confirm all three inscriptions still render as signs and still print their own line of prose when right-clicked:

```bash
./gradlew :mod:hytale:shadowJar
cp mod/hytale/build/libs/*-all.jar .scratch/act-ii-verify/mods/
```

Expected: identical behaviour to Task 5 Step 6. If any inscription loses its model or its message, `Parent` did not carry what Step 7 suggested — revert this task with `git checkout -- mod/hytale/src/main/resources/Server/Item/Items/` and record the discrepancy for Task 7.

- [ ] **Step 6: Commit**

```bash
git add mod/hytale/src/main/resources/Server/Item/Items
git commit -m "refactor(mod): the inscriptions inherit from a shared base

Task 5 verified Parent inheritance on a real server, including the nested
Interactions case, so the three near-identical inscription files collapse to a
base plus what actually differs.

Done now rather than later because the volume ahead makes duplication the
default shape otherwise: Act IV's Circle is 5-7 tier assets differing by a
number, the Class A-E materials share a backbone, and questline #2 is the whole
set again per element.

The statue is deliberately not converted — different model, hitbox, gathering
and scale. Inheriting it from a sign would be inheritance for its own sake."
```

---

### Task 7: Record what this proved, and ship it

**Files:**
- Modify: `docs/integration/hytale-asset-packs.md`
- Modify: `docs/stormseeker/stormseeker-canonical.md`

**Interfaces:**
- Consumes: the findings recorded in Task 5, Step 9, and whether Task 6 ran.
- Produces: the durable record. Nothing depends on this task in code.

`hytale-asset-packs.md` describes itself as the durable mechanism document, more durable than the capability audit. Two mechanisms this plan uses are absent from it, and both are load-bearing for the delivery model.

- [ ] **Step 1: Record the translation mechanism**

In `docs/integration/hytale-asset-packs.md`, add a new section after §7 ("Extending the vocabulary from a mod"):

```markdown
## 7b. A mod pack can ship translations — and this is how text reaches players

`ObjectiveAsset.TitleId` and `DescriptionId` are **localisation keys, not literal text**, so
the objective-text delivery channel does not work at all without this.

`I18nModule.setup` calls `AssetModule.getAssetPacks()`, iterates it, and calls
`loadMessagesFromPack(pack)` for each, which resolves `pack.getRoot()/Server/Languages` and
reads every `.lang` beneath it. So a mod jar shipping
`Server/Languages/en-US/server.lang` contributes translations exactly as it contributes
assets.

**The filename is the key prefix.** A key written in `server.lang` as
`objectives.X.title` resolves as `server.objectives.X.title`, which is why the base game's
own file contains `objectives.Objective_Kill.title` and the schema's editor template reads
`server.objectives.{assetId}.title`.

The conventions the base game uses, all confirmed in `Server/Languages/en-US/server.lang`:

| Key | What it names |
|---|---|
| `objectives.<AssetId>.title` | objective title |
| `objectives.<AssetId>.desc` | objective description |
| `objectives.<AssetId>.taskSet.<i>.task.<j>` | one task's line — **each task gets its own string** |
| `items.<ItemId>.name` | item/block name |

Values support inline markup: `Gather <item is="Soil_Dirt"/>`.

> **Translations are global.** A key we ship that collides with a base-game key replaces that
> text for every player on the server, including players who never touch our content. This is
> the same hazard as an asset filename collision and it has the same shape: designed
> behaviour, invisible to the compiler, fails green. `mod/hytale`'s `checkAssetPackIntegrity`
> guard requires every key we ship to carry a scoped prefix for this reason.
```

- [ ] **Step 2: Record the block-interaction pattern**

In the same file, add after the section from Step 1:

```markdown
## 7c. Attaching behaviour to a block, without overriding one

The shipped `Server/Item/Items/MISC/Test_Objective_Line_Interaction.json` is the reference
pattern: a block item whose `Interactions.Secondary` inlines a `RootInteraction` whose
`Interactions` array starts an objective line on right-click.

`Interactions.Secondary` accepts either a **string reference** to a `RootInteraction` asset or
an **inline** one. `SendMessageInteraction` carries either a literal `Message` or a
localisation `Key`, and its `Next` field — a **single** interaction, not an array — runs on
success, which is how a message chains into `StartObjective` with unambiguous ordering.

The constraint worth stating: **you cannot attach an interaction to a shipped block.** Asset
ids come from filenames, so adding behaviour to `Furniture_Temple_Wind_Sign` means shipping a
file by that name, which replaces the block for everyone. Ship a new id that reuses the base
`CustomModel` and `CustomModelTexture` paths instead — cross-pack references resolve, so the
new block is visually identical to the palette around it.
```

- [ ] **Step 3: Fold in what Task 5 actually established**

Read `.scratch/act-ii-verify/README.md` from Task 5 Step 9 and correct the two sections above wherever the run disagrees with them. In particular, if pack translations did **not** load, say so plainly and rewrite §7b as a negative finding rather than deleting it — a mechanism that was checked and does not work is worth more than silence.

Then add a short section recording the `Parent` result, whichever way it went:

```markdown
## 7d. `Parent` inheritance — what it actually carries

Every asset schema has a `Parent` field, documented as inheriting the parent's
properties with the child's values replacing them field by field, "in the case of
nested structures … within the structure".

Tested 2026-08-25 against server `0.5.9` by giving one inscription block a `Parent`
and nothing else but a name: <RECORD THE THREE ANSWERS — did it load, did the model
come through, did the nested `Interactions` chain come through>.

This matters past one questline. Act IV's Circle is raised in 5–7 tiers that differ by
a number, the Class A–E materials share a backbone, and a per-element questline family
repeats the whole asset set per element. Whether near-identical asset files are the
default shape of that work turns on this field.
```

Replace the angle-bracket span with the observed answers before committing. **Do not leave it as written** — an unfilled placeholder in the durable mechanism document is worse than no section.

Then update §8b ("What this still does *not* establish"). Remove any bullet the play-through closed and add anything it opened. `WeatherTriggerCondition` remains untested — Act II does not exercise it.

- [ ] **Step 4: Update the questline's Implementation Status**

In `docs/stormseeker/stormseeker-canonical.md`, in the *Implementation Status* section, move Act II out of the "Does not exist" list and add a paragraph after "Exists and is live":

```markdown
**Act II ships as native assets.** `mod/hytale` is an asset pack
(`"IncludesAssetPack": true`) carrying `ObjectiveLine_Stormseeker_TheSixthCircle`, two
objectives, a reach-location marker, four inscription blocks and one `server.lang`. Reading
the first inscription starts the line; reading the record completes the act. Verified by
playing it on a real server with a connected client.

**The ruin is not generated.** The four blocks are placed by hand and the chamber marker is
sited with `/objective reachLocationMarker add ReachLocationMarker_Stormseeker_RuinChamber`.
A `Temple_Wind` prefab in worldgen is separate work, still blocked on the open Zone-2
anchoring question, and on whether a mod pack can contribute worldgen structures at all —
which remains unproven.

**Act I's trail is still the specced route in and does not exist.** Act II is reachable
without it because the ruin is deliberately not quest-gated; when Act I lands, its trail
becomes a second route to the same place.
```

Also strike the line under *Not yet done for the native spine* that reads `mod/hytale`'s `manifest.json` still reads `"IncludesAssetPack": false`, and the module has no `Server/Objective/...` asset tree — both are now false.

- [ ] **Step 5: Run the full build and both censuses**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`.

Run each **bare**, then read its named verdict line:

```bash
python3 scripts/test-census.py
```

```bash
python3 scripts/coverage-census.py
```

- [ ] **Step 6: Commit**

```bash
git add docs/integration/hytale-asset-packs.md docs/stormseeker/stormseeker-canonical.md
git commit -m "docs: record pack translations and the block-interaction pattern

hytale-asset-packs.md is the durable mechanism document and was missing both
mechanisms Act II depends on. TitleId/DescriptionId are localisation keys, so
the objective-text delivery channel does not work at all without pack-shipped
lang files.

Also records the constraint that behaviour cannot be attached to a shipped
block without replacing it, and updates the questline's Implementation Status."
```

- [ ] **Step 7: Open the PR**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git push -u origin feat/stormseeker-act-ii-the-trace
gh pr create --base main --title "Stormseeker Act II — The Trace, as native assets" --body "$(cat <<'BODY'
Ships Act II as real content, authored entirely as Hytale asset JSON. Reading an
inscription in the ruin starts the questline; reading the builders' record completes
the act. Verified by playing it on a real server with a connected client, not by
checking the server booted.

**What landed**

- `mod/hytale` is an asset pack: `IncludesAssetPack: true`, ten assets, one `server.lang`.
- `AssetPackIntegrityCheck` in `buildSrc`, wired into `check`. Asset ids come from
  filenames and lang keys are global, so a mis-named file or key silently **overrides**
  the base game while the build stays green. The guard requires a Stormseeker-scoped
  prefix on both, so it needs no copy of the base asset list and survives game updates.
  It reads files rather than classes, which makes it the second check giving CI real
  coverage of this module.
- Four inscription blocks reusing the shipped `Temple_Wind` models under new ids —
  attaching behaviour to the shipped blocks would have replaced them for every player.

**Deliberately not in scope**

- The ruin is not generated. Blocks are placed by hand, the chamber marker sited by
  command. Worldgen from a mod pack is unproven and collides with the open Zone-2
  anchoring question.
- Act I's trail is not built, so discovery is the entry — which the spec allows, since
  the ruin is not quest-gated.
- No reward and no `NextObjectiveLineIds`; Act III does not exist.

Plan: `docs/superpowers/plans/2026-08-25-stormseeker-act-ii-the-trace.md`
BODY
)"
```

- [ ] **Step 8: Merge**

```bash
gh pr merge <n> --merge --delete-branch
```

**Merge, never squash or rebase.** Squash collapses the deliberate separation between the guard, the content and the docs; rebase rewrites SHAs cited across the knowledge vault.

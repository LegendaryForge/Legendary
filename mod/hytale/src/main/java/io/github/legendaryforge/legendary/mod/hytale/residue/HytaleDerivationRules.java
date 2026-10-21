package io.github.legendaryforge.legendary.mod.hytale.residue;

import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import java.util.List;
import java.util.Set;

/**
 * The roster's groundings, in the only module allowed to name Hytale assets.
 *
 * <p>{@code :core} holds the grounding <em>types</em> and the scoring; it may not hold these ids,
 * because {@code Fluid_Lava} and {@code Env_Zone4_Volcanoes} are Hytale's and {@code :core} is
 * engine-agnostic by build guard. That separation is also what keeps the roster a reversible
 * documentation decision rather than an API: there is no enum of elements anywhere, only these
 * strings.
 *
 * <p><strong>Roster: the cosmological five</strong>, decided 2026-10-21 — and grouped here by how
 * each is grounded, because that is not uniform and a single rule shape got two of them wrong.
 *
 * <p><strong>Void is absent, by decision.</strong> It is a staged incursion the engine runs inside a
 * Fragment on a timer and ends by eviction, and its droppers span 16 environments across three zones
 * with no terrain character. Nothing about terrain should site it. Ice and Life are absent too: Ice
 * is modelled inside Water (Water's own text covers *"the magic of water and ice"*, so glacial
 * terrain is Water's to claim), and Life is farmed rather than sited — 1 NPC dropper against 64 crop
 * tables.
 *
 * <p><strong>Every id here was verified present in build 0.7.2</strong> rather than guessed: the
 * fluids are the contents of {@code Server/BlockTypeList/Fluids.json}, and the environment
 * substrings were matched against the 134 shipped {@code Server/Environments} assets. A rule naming
 * an asset that does not exist would score zero forever and look exactly like terrain that does not
 * qualify.
 *
 * <p><strong>Why substrings and not the asset tags.</strong> Environments carry structured
 * {@code Tags} in their JSON — {@code Volcanic}, {@code Shores}, {@code Glacial}, {@code Mountains} —
 * which would be the principled key. They are not usable: there is <strong>no tag accessor on the
 * runtime {@code Environment} object</strong>, only {@code getId()}. And they are incomplete anyway —
 * 11 environments are volcanic by name against 5 by tag, the gap being exactly the {@code _T2} and
 * {@code _T3} tiers, and 29 of 134 environments carry no sub-tag at all. So names are both the only
 * runtime option and the more complete one. Measured, not assumed.
 */
public final class HytaleDerivationRules {

    private HytaleDerivationRules() {}

    /**
     * The five groundings, as a starting position for tuning rather than a settled answer.
     *
     * <p>Known gaps, stated because the probe exists to expose them:
     *
     * <ul>
     *   <li><strong>River versus ocean is not expressible.</strong> No {@code River}, {@code Ocean}
     *       or {@code Lake} environment ships, there is no sea-level constant in the jar, and no
     *       river or ocean worldgen node exists. Water is water.
     *   <li><strong>Earth leans on caves</strong>, the cheapest enclosure proxy available without
     *       block-level sampling. It will miss exposed mountain stone.
     *   <li><strong>Lightning's storm gate is a guess.</strong> 0.10 sits well above Zone 1's ~0.014
     *       and well below the Crucible's 0.50, but nothing has measured the distribution between
     *       them across all 134 environments.
     * </ul>
     */
    public static List<ElementGrounding> cosmologicalFive() {
        return List.of(
                // --- Physical: the fluid is required. A volcanic region with no lava is not a Fire
                // site, which is the defect the Physical type exists to prevent.
                new ElementGrounding.Physical(
                        "fire", Set.of("Fluid_Lava"), Set.of("Volcanoes", "Caves_Volcanic", "Crucible", "Wastes")),

                // Glacial is corroboration here rather than a separate Ice element: the roster
                // decision models Ice inside Water.
                new ElementGrounding.Physical(
                        "water", Set.of("Fluid_Water"), Set.of("Shores", "Swamps", "Glacial", "Caves_Swamps", "Oasis")),

                // --- Geometric: terrain shape, no marker to require. Both halves needed.
                // Open sky is the discriminator, so the band is tight.
                new ElementGrounding.Geometric(
                        "wind", Set.of("Mountains", "Peaks", "Highlands", "Plateaus", "Cliffs"), 12, 15),

                // Deliberately the inverse exposure band: enclosed stone, not open ground. A cave
                // environment read at high sky light is a cave mouth, and scores lower for it.
                new ElementGrounding.Geometric("earth", Set.of("Caves", "Deserts", "Badlands", "Mineshafts"), 0, 6),

                // --- Weather: no physical presence at all. Storm probability comes from the
                // environment's own WeatherForecasts weights, which is why this is the one grounding
                // where an environment id is the primary signal rather than a coarse proxy.
                new ElementGrounding.Weather("lightning", 0.10, 13, 15));
    }
}

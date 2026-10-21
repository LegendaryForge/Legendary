package io.github.legendaryforge.legendary.mod.hytale.residue;

import io.github.legendaryforge.legendary.core.api.residue.ElementRule;
import java.util.List;
import java.util.Set;

/**
 * The roster's scoring rules, in the only module allowed to name Hytale assets.
 *
 * <p>{@code :core} holds the rule <em>type</em> and the scoring; it may not hold these ids, because
 * {@code Fluid_Lava} and {@code Env_Zone4_Volcanoes} are Hytale's and {@code :core} is
 * engine-agnostic by build guard. That separation is also what keeps the roster a reversible
 * documentation decision rather than an API: there is no enum of elements anywhere, here or in
 * {@code :core} — only these strings, and strings are cheap to change.
 *
 * <p><strong>Roster: the cosmological five</strong>, decided 2026-10-21. Void is absent on purpose —
 * it is an incursion rather than a feature of ground, and its droppers span 16 environments across
 * three zones with no terrain character at all. Ice and Life are absent too: Ice is modelled inside
 * Water (Water's own text covers *"the magic of water and ice"*, so the glacial environments are
 * Water's), and Life is farmed rather than sited, 1 NPC dropper against 64 crop tables.
 *
 * <p><strong>Every id here was verified present in build 0.7.2</strong> rather than guessed: the six
 * fluids are the contents of {@code Server/BlockTypeList/Fluids.json}, and the environment
 * substrings were matched against the 134 shipped {@code Server/Environment} assets. A rule naming
 * an asset that does not exist would score zero forever and look exactly like terrain that does not
 * qualify, which is the failure this note exists to prevent.
 */
public final class HytaleDerivationRules {

    private HytaleDerivationRules() {}

    /**
     * The five rules, as a starting position for tuning rather than a settled answer.
     *
     * <p>Known gaps, stated because the probe exists to expose them:
     *
     * <ul>
     *   <li><strong>River versus ocean is not expressible.</strong> No {@code River}, {@code Ocean}
     *       or {@code Lake} environment ships, there is no sea-level constant in the jar, and no
     *       river or ocean worldgen node exists. Water is water. Telling a river from a coastline
     *       needs a heuristic we invent — extent by bounded sampling, depth, or {@code Shores}
     *       adjacency — and none of it is here yet.
     *   <li><strong>Lightning is approximated by exposure alone.</strong> §5 ties it to storm
     *       frequency, which was measured per environment in session 16 (Zone 1 at 1.4%,
     *       {@code Env_Zone4_Crucible} at 50%) but is not available through the terrain port. Until
     *       it is, Lightning shares Wind's signals and will over-report.
     *   <li><strong>Earth leans on caves</strong>, which is the cheapest stone proxy available
     *       without block-level sampling. It will miss exposed mountain stone.
     * </ul>
     */
    public static List<ElementRule> cosmologicalFive() {
        return List.of(
                // Lava is unambiguous and it is the strongest signal any of these rules has.
                // Volcanoes covers the Zone 4 surface; Caves_Volcanic covers the T1/T2/T3 tiers in
                // Zones 1-3 by substring, so the tiers need no enumerating.
                new ElementRule("fire", Set.of("Fluid_Lava"), Set.of("Volcanoes", "Caves_Volcanic"), 0, 15),

                // Glacial is here rather than under an Ice rule: the roster decision models Ice
                // inside Water, so glacial terrain is Water's to claim.
                new ElementRule(
                        "water", Set.of("Fluid_Water"), Set.of("Shores", "Swamps", "Glacial", "Caves_Swamps"), 0, 15),

                // Open sky is the discriminator, so the range is tight. The rule still needs a
                // locational match first -- sky light alone qualifies nothing, by design in
                // SiteScorer, or every open column in the world becomes a Wind site.
                new ElementRule("wind", Set.of(), Set.of("Mountains", "Peaks", "Highlands", "Cliffs"), 12, 15),

                // Shares Wind's exposure requirement and will therefore over-report until storm
                // frequency reaches the terrain port. Recorded as a known approximation, not a
                // finding.
                new ElementRule("lightning", Set.of(), Set.of("Mountains", "Peaks", "Highlands", "Crucible"), 13, 15),

                // Deliberately the inverse exposure band: enclosed stone, not open ground.
                new ElementRule("earth", Set.of(), Set.of("Caves", "Deserts", "Badlands"), 0, 6));
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.persistence;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerPhase;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists StormseekerProgress to/from disk using Java Properties files.
 *
 * <p>Note this is NOT an implementation of the {@code mod.runtime.StormseekerProgressStore}
 * port: that is keyed by {@code PlayerRef} and returns {@code Optional}, while this is keyed
 * by a raw player-id string and substitutes a fresh progress for a missing or unreadable
 * file. The two were unrelated types sharing one simple name until 2026-08-24.
 *
 * <p>One file per player: {dataDir}/{playerId}.properties
 *
 * <p>Format:
 * <pre>
 * phase=PHASE_4_THE_TRIALS
 * lodestoneCoursing=true
 * lodestoneStanding=false
 * </pre>
 *
 * <p>Files written before 2026-10-21 use {@code sigilA} / {@code sigilB} for the two proofs and
 * are migrated on read. See {@link #LEGACY_KEY_COURSING}.
 */
public final class PropertiesProgressStore {

    private static final String KEY_COURSING = "lodestoneCoursing";
    private static final String KEY_STANDING = "lodestoneStanding";

    /**
     * Key names used before the 2026-10-21 sigil -> lodestone rename. Read on load, never written:
     * migration is one-way, so the first save after an upgrade drops them.
     *
     * <p>Without this fallback the rename would be silent data loss rather than a visible failure.
     * A pre-rename file parses perfectly — it simply has no lodestone keys — so {@link #load}
     * would return both proofs as {@code false}, {@link #quarantine} would never fire, and the
     * next disconnect would save that fresh state over a player's completed trials.
     */
    private static final String LEGACY_KEY_COURSING = "sigilA";

    private static final String LEGACY_KEY_STANDING = "sigilB";

    private final Path dataDir;

    public PropertiesProgressStore(Path dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Loads progress for a player from disk.
     * Returns a fresh StormseekerProgress if no save file exists.
     */
    public StormseekerProgress load(String playerId) {
        Path file = resolveFile(playerId);
        if (!Files.exists(file)) {
            return new StormseekerProgress();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Properties props = new Properties();
            props.load(reader);

            StormseekerProgress progress = new StormseekerProgress();

            // Advance to saved phase
            String phaseName = props.getProperty("phase", "UNTOUCHED");
            StormseekerPhase targetPhase = StormseekerPhase.valueOf(phaseName);
            while (progress.phase() != targetPhase && !progress.phase().isFinal()) {
                progress.advanceToNextOrThrow(progress.phase().next());
            }

            // Restore lodestones, accepting pre-rename key names (see LEGACY_KEY_COURSING)
            if (readProof(props, KEY_COURSING, LEGACY_KEY_COURSING)) {
                progress.grantCoursingLodestone();
            }
            if (readProof(props, KEY_STANDING, LEGACY_KEY_STANDING)) {
                progress.grantStandingLodestone();
            }

            return progress;
        } catch (Exception e) {
            System.err.println("[Stormseeker] Failed to load progress for " + playerId + ": " + e.getMessage());
            quarantine(file, playerId);
            return new StormseekerProgress();
        }
    }

    /**
     * Saves progress for a player to disk.
     */
    public void save(String playerId, StormseekerProgress progress) {
        Path file = resolveFile(playerId);

        try {
            Files.createDirectories(dataDir);

            Properties props = new Properties();
            props.setProperty("phase", progress.phase().name());
            props.setProperty(KEY_COURSING, String.valueOf(progress.hasCoursingLodestone()));
            props.setProperty(KEY_STANDING, String.valueOf(progress.hasStandingLodestone()));

            try (Writer writer = Files.newBufferedWriter(file)) {
                props.store(writer, "Stormseeker quest progress for " + playerId);
            }
        } catch (IOException e) {
            System.err.println("[Stormseeker] Failed to save progress for " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * Saves all players' progress.
     */
    public void saveAll(
            Iterable<String> playerIds, java.util.function.Function<String, StormseekerProgress> progressLookup) {
        for (String playerId : playerIds) {
            save(playerId, progressLookup.apply(playerId));
        }
    }

    /**
     * Reads a proof flag, falling back to its pre-rename key name only when the current key is
     * absent. Presence, not truth, decides: an explicit {@code lodestoneCoursing=false} wins over
     * a stale {@code sigilA=true}, so a post-rename save is never overridden by a legacy leftover.
     */
    private static boolean readProof(Properties props, String key, String legacyKey) {
        String value = props.getProperty(key);
        if (value == null) {
            value = props.getProperty(legacyKey, "false");
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Moves aside a save file that could not be read, so a failed load cannot become data loss.
     *
     * <p>Without this, an unreadable file was left exactly where it was and {@link #load} returned
     * fresh progress. The caller then saved that fresh progress on disconnect, overwriting the file
     * it had failed to read — so one bad read permanently replaced a finished questline with
     * PHASE_0, and the original bytes were gone before anyone could look at them.
     *
     * <p>Deliberately best-effort and silent-on-failure: {@code load} must stay total, because it
     * runs on player connect and throwing there would turn an unreadable save into a failed login.
     * A name collision needs two failed loads for one player inside the same millisecond; the move
     * then fails and is logged, leaving the newer file in place.
     */
    private void quarantine(Path file, String playerId) {
        Path target = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, target);
            System.err.println("[Stormseeker] Preserved unreadable save for " + playerId + " at " + target);
        } catch (IOException moveFailure) {
            System.err.println("[Stormseeker] Could not preserve unreadable save for " + playerId + ": "
                    + moveFailure.getMessage());
        }
    }

    private Path resolveFile(String playerId) {
        // Sanitize player ID for filesystem safety
        String safe = playerId.replaceAll("[^a-zA-Z0-9\\-]", "_");
        return dataDir.resolve(safe + ".properties");
    }
}

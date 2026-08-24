package io.github.legendaryforge.legendary.mod.stormseeker.persistence;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
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
 * phase=PHASE_2_DUAL_SIGILS
 * sigilA=true
 * sigilB=false
 * </pre>
 */
public final class PropertiesProgressStore {

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
            String phaseName = props.getProperty("phase", "PHASE_0_WATCHING_ELEMENTAL");
            StormseekerPhase targetPhase = StormseekerPhase.valueOf(phaseName);
            while (progress.phase() != targetPhase && !progress.phase().isFinal()) {
                progress.advanceToNextOrThrow(progress.phase().next());
            }

            // Restore sigils
            if (Boolean.parseBoolean(props.getProperty("sigilA", "false"))) {
                progress.grantSigilA();
            }
            if (Boolean.parseBoolean(props.getProperty("sigilB", "false"))) {
                progress.grantSigilB();
            }

            return progress;
        } catch (Exception e) {
            System.err.println("[Stormseeker] Failed to load progress for " + playerId + ": " + e.getMessage());
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
            props.setProperty("sigilA", String.valueOf(progress.hasSigilA()));
            props.setProperty("sigilB", String.valueOf(progress.hasSigilB()));

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

    private Path resolveFile(String playerId) {
        // Sanitize player ID for filesystem safety
        String safe = playerId.replaceAll("[^a-zA-Z0-9\\-]", "_");
        return dataDir.resolve(safe + ".properties");
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerPhase;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Characterization tests for the on-disk progress store.
 *
 * <p>This class carried zero test coverage while living in {@code mod/hytale}, where it could
 * not be tested on any machine without the game jar — that module excludes {@code **}{@code
 * /hytale/**} wholesale when the jar is absent, test sources included. It has no platform
 * references at all, so the coverage gap was a placement problem rather than a testability one.
 */
final class PropertiesProgressStoreTest {

    private static StormseekerProgress at(StormseekerPhase phase) {
        StormseekerProgress progress = new StormseekerProgress();
        while (progress.phase() != phase && !progress.phase().isFinal()) {
            progress.advanceToNextOrThrow(progress.phase().next());
        }
        return progress;
    }

    @Test
    void load_withPreRenameKeys_migratesSigilAAndSigilB(@TempDir Path dir) throws IOException {
        // Saves written before the 2026-10-21 sigil -> lodestone rename use the old key names.
        // Dropping them would silently reset a player's trial progress to zero: load() would see
        // no lodestone keys, return false for both, and the next disconnect would overwrite the
        // file with that fresh state. Quarantine does not catch this -- the file parses fine.
        Files.writeString(dir.resolve("legacy.properties"), "phase=PHASE_4_THE_TRIALS\nsigilA=true\nsigilB=true\n");

        StormseekerProgress loaded = new PropertiesProgressStore(dir).load("legacy");

        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, loaded.phase());
        assertTrue(loaded.hasCoursingLodestone(), "pre-rename sigilA must restore as the Coursing Lodestone");
        assertTrue(loaded.hasStandingLodestone(), "pre-rename sigilB must restore as the Standing Lodestone");
    }

    @Test
    void load_withPreRenameKeys_migratesEachIndependently(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("half.properties"), "phase=PHASE_4_THE_TRIALS\nsigilA=true\nsigilB=false\n");

        StormseekerProgress loaded = new PropertiesProgressStore(dir).load("half");

        assertTrue(loaded.hasCoursingLodestone());
        assertFalse(loaded.hasStandingLodestone(), "sigilB=false must not be migrated as granted");
    }

    @Test
    void load_prefersNewKeysOverLegacyWhenBothPresent(@TempDir Path dir) throws IOException {
        // A file saved after the rename but hand-edited, or written by a mixed-version pair of
        // servers. The current key wins; the legacy key must not override it.
        Files.writeString(
                dir.resolve("both.properties"), "phase=PHASE_4_THE_TRIALS\nlodestoneCoursing=false\nsigilA=true\n");

        StormseekerProgress loaded = new PropertiesProgressStore(dir).load("both");

        assertFalse(loaded.hasCoursingLodestone(), "explicit lodestoneCoursing=false must win over legacy sigilA=true");
    }

    @Test
    void save_afterMigration_writesOnlyCurrentKeys(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("legacy2.properties"), "phase=PHASE_4_THE_TRIALS\nsigilA=true\nsigilB=true\n");
        var store = new PropertiesProgressStore(dir);

        store.save("legacy2", store.load("legacy2"));

        String rewritten = Files.readString(dir.resolve("legacy2.properties"));
        assertTrue(rewritten.contains("lodestoneCoursing=true"), "must write the current key");
        assertTrue(rewritten.contains("lodestoneStanding=true"), "must write the current key");
        assertFalse(rewritten.contains("sigilA"), "legacy key must not be re-emitted -- migration is one-way");
        assertFalse(rewritten.contains("sigilB"), "legacy key must not be re-emitted -- migration is one-way");
    }

    @Test
    void load_withNoSaveFile_returnsFreshProgress(@TempDir Path dir) {
        StormseekerProgress progress = new PropertiesProgressStore(dir).load("nobody");

        assertEquals(StormseekerPhase.UNTOUCHED, progress.phase());
        assertFalse(progress.hasCoursingLodestone());
        assertFalse(progress.hasStandingLodestone());
    }

    @Test
    void save_thenLoad_roundTripsPhaseAndBothLodestones(@TempDir Path dir) {
        var store = new PropertiesProgressStore(dir);
        StormseekerProgress saved = at(StormseekerPhase.PHASE_4_THE_TRIALS);
        saved.grantCoursingLodestone();
        saved.grantStandingLodestone();

        store.save("p1", saved);
        StormseekerProgress loaded = store.load("p1");

        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, loaded.phase());
        assertTrue(loaded.hasCoursingLodestone());
        assertTrue(loaded.hasStandingLodestone());
    }

    @Test
    void save_thenLoad_keepsLodestonesIndependent(@TempDir Path dir) {
        var store = new PropertiesProgressStore(dir);
        StormseekerProgress saved = at(StormseekerPhase.PHASE_4_THE_TRIALS);
        saved.grantCoursingLodestone();

        store.save("p1", saved);
        StormseekerProgress loaded = store.load("p1");

        assertTrue(loaded.hasCoursingLodestone());
        assertFalse(loaded.hasStandingLodestone(), "lodestoneStanding was never granted and must not be restored");
    }

    @Test
    void save_createsTheDataDirectoryWhenAbsent(@TempDir Path dir) {
        Path nested = dir.resolve("does/not/exist/yet");
        new PropertiesProgressStore(nested).save("p1", new StormseekerProgress());

        assertTrue(Files.isDirectory(nested), "save must create its data directory");
        assertTrue(Files.exists(nested.resolve("p1.properties")));
    }

    @Test
    void load_withUnknownPhaseName_fallsBackToFreshProgress(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("p1.properties"), "phase=PHASE_9_NOT_A_REAL_PHASE\nlodestoneCoursing=true\n");

        StormseekerProgress loaded = new PropertiesProgressStore(dir).load("p1");

        // load stays total: a bad save must not break player connect.
        assertEquals(StormseekerPhase.UNTOUCHED, loaded.phase());
        assertFalse(loaded.hasCoursingLodestone());
    }

    @Test
    void load_withUnreadableFile_returnsFreshProgressRatherThanThrowing(@TempDir Path dir) throws IOException {
        // A directory where the properties file should be: newBufferedReader throws IOException.
        Files.createDirectories(dir.resolve("p1.properties"));

        assertDoesNotThrow(() -> {
            StormseekerProgress loaded = new PropertiesProgressStore(dir).load("p1");
            assertEquals(StormseekerPhase.UNTOUCHED, loaded.phase());
        });
    }

    @Test
    void load_thatFails_quarantinesTheOriginalInsteadOfLeavingItToBeOverwritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("p1.properties");
        String original = "phase=PHASE_6_THE_FORGING\nlodestoneCoursing=true\nlodestoneStanding=true\n";
        Files.writeString(file, original);
        var store = new PropertiesProgressStore(dir);

        // Connect: the phase name is fine, but suppose the file cannot be parsed at all.
        Files.writeString(file, "\u0000not a properties file\nphase=\\uZZZZ\n");
        store.load("p1");

        List<Path> quarantined;
        try (var stream = Files.list(dir)) {
            quarantined = stream.filter(f -> f.getFileName().toString().contains(".corrupt-"))
                    .toList();
        }
        assertEquals(
                1, quarantined.size(), "an unreadable save must be preserved, not left in place to be overwritten");
    }

    @Test
    void failedLoad_thenSave_doesNotDestroyTheOriginalBytes(@TempDir Path dir) throws IOException {
        // The real lifecycle: HytaleStormseekerHost loads on connect and saves on
        // disconnect. Before quarantining, a single unreadable read permanently
        // replaced a finished questline with PHASE_0 the moment the player left.
        Path file = dir.resolve("p1.properties");
        String original = "phase=PHASE_6_THE_FORGING\nlodestoneCoursing=true\nlodestoneStanding=true\n";
        Files.writeString(file, original);
        var store = new PropertiesProgressStore(dir);
        Files.writeString(file, "phase=PHASE_9_NOT_A_REAL_PHASE\n");

        StormseekerProgress loaded = store.load("p1");
        store.save("p1", loaded);

        String preserved;
        try (var stream = Files.list(dir)) {
            Path quarantine = stream.filter(f -> f.getFileName().toString().contains(".corrupt-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no quarantine file was written"));
            preserved = Files.readString(quarantine);
        }
        assertTrue(preserved.contains("PHASE_9_NOT_A_REAL_PHASE"), "the unreadable bytes must survive for recovery");
    }

    @Test
    void load_withNoFileAtAll_quarantinesNothing(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        new PropertiesProgressStore(dir).load("newcomer");

        try (var stream = Files.list(dir)) {
            assertEquals(0, stream.count(), "a first-time player is not a corrupt save");
        }
    }

    @Test
    void playerIds_areSanitisedForTheFilesystem(@TempDir Path dir) {
        new PropertiesProgressStore(dir).save("../../etc/passwd", new StormseekerProgress());

        assertTrue(
                Files.exists(dir.resolve("______etc_passwd.properties")),
                "path separators and dots must not escape the data directory");
    }

    @Test
    void playerIds_differingOnlyInSanitisedCharacters_collide(@TempDir Path dir) {
        var store = new PropertiesProgressStore(dir);
        StormseekerProgress advanced = at(StormseekerPhase.PHASE_2_THE_TREK);

        store.save("a.b", advanced);
        StormseekerProgress other = store.load("a/b");

        // Both sanitise to "a_b". LATENT, not live: the only production caller passes
        // playerRef.getUuid().toString(), which is [0-9a-f-] and never rewritten, so the
        // sanitiser is a no-op today and no two real ids can collide. Nothing enforces
        // that, though — the parameter is a String, so a future caller passing a username
        // makes this live immediately. Pinned so that change fails a test instead of
        // silently merging two players' progress; not worth a key-encoding migration while
        // the sole caller keeps it unreachable.
        assertEquals(StormseekerPhase.PHASE_2_THE_TREK, other.phase());
    }

    @Test
    void saveAll_writesEveryPlayerViaTheLookup(@TempDir Path dir) {
        var store = new PropertiesProgressStore(dir);
        Map<String, StormseekerProgress> progressById = Map.of(
                "p1", at(StormseekerPhase.PHASE_2_THE_TREK),
                "p2", at(StormseekerPhase.PHASE_4_THE_TRIALS));

        store.saveAll(List.of("p1", "p2"), progressById::get);

        assertEquals(StormseekerPhase.PHASE_2_THE_TREK, store.load("p1").phase());
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, store.load("p2").phase());
    }
}

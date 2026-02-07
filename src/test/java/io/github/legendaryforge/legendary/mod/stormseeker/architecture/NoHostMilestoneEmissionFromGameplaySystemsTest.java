package io.github.legendaryforge.legendary.mod.stormseeker.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Architectural guardrail:
 * - Gameplay/ECS-side systems mutate progress/state only.
 * - Host tick seams observe durable edges and emit milestones/hints/host-facing outputs.
 *
 * This test prevents gameplay classes from acquiring a dependency on host runtime / host tick seams.
 */
final class NoHostMilestoneEmissionFromGameplaySystemsTest {

    @Test
    void gameplayClassesMustNotDependOnHostRuntimeOrHostTicks() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir"));

        // Gameplay-side classes that MUST NOT emit milestones or call host hooks.
        List<Path> gameplayFiles = List.of(
                repoRoot.resolve(
                        "src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial/anchored/AnchoredSigilGrantSystem.java"),
                repoRoot.resolve(
                        "src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial/anchored/AnchoredTrialSession.java"),
                repoRoot.resolve(
                        "src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial/flowing/FlowingTrialSession.java"),
                repoRoot.resolve(
                        "src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial/flowing/FlowingTrialEvaluator.java"));

        for (Path f : gameplayFiles) {
            assertTrue(Files.exists(f), "Expected file to exist: " + f);

            String src = Files.readString(f);

            // Must not depend on host runtime interface.
            assertFalse(
                    src.contains("import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;"),
                    "Gameplay class must not import StormseekerHostRuntime: " + f);

            // Must not depend on host tick/driver seams (prevents milestone emission sneaking in).
            assertFalse(
                    src.contains("import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostTick;")
                            || src.contains(
                                    "import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostDriver;")
                            || src.contains(
                                    "import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostTick;")
                            || src.contains(
                                    "import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostDriver;"),
                    "Gameplay class must not import host tick/driver seams: " + f);
        }
    }
}

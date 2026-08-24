package io.github.legendaryforge.legendary.mod.stormseeker.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architectural guardrail:
 * - Gameplay/ECS-side code mutates progress/state only.
 * - Host tick seams observe durable edges and emit milestones/hints/host-facing outputs.
 *
 * This test prevents gameplay packages from acquiring a dependency on host runtime / host tick seams.
 */
final class NoHostMilestoneEmissionFromGameplaySystemsTest {

    private static final String HOST_RUNTIME_IMPORT =
            "import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;";
    private static final List<String> HOST_SEAM_IMPORTS = List.of(
            "import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostTick;",
            "import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostDriver;",
            "import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostTick;",
            "import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostDriver;");

    @Test
    void gameplayPackagesMustNotDependOnHostRuntimeOrHostTicks() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir"));

        // Scan gameplay-side source packages (expand this list if/when more gameplay packages are introduced).
        List<Path> roots = List.of(
                repoRoot.resolve("src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial"),
                repoRoot.resolve("src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/progress"));

        List<Path> javaFiles = new ArrayList<>();
        for (Path r : roots) {
            if (!Files.exists(r)) {
                continue; // progress package may not exist yet; keep guardrail resilient.
            }
            try (Stream<Path> st = Files.walk(r)) {
                st.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
            }
        }

        assertFalse(javaFiles.isEmpty(), "Expected at least one gameplay java file to be scanned");

        for (Path f : javaFiles) {
            String src = Files.readString(f);

            assertFalse(
                    src.contains(HOST_RUNTIME_IMPORT), "Gameplay class must not import StormseekerHostRuntime: " + f);

            for (String imp : HOST_SEAM_IMPORTS) {
                assertFalse(src.contains(imp), "Gameplay class must not import host seam (" + imp + "): " + f);
            }
        }
    }
}

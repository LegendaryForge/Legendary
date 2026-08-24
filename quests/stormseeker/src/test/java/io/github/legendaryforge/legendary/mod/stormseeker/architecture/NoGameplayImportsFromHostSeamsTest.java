package io.github.legendaryforge.legendary.mod.stormseeker.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Engine integration layering guardrail:
 *
 * <p>The engine/ECS integration layer should depend on host seams and services, not on trial/gameplay
 * internals. This prevents accidental double-ticking, implicit cleanup behavior, or milestone leakage.
 *
 * <p>Scope: we intentionally scan only the integration layer (and StormseekerWiring) because runtime/quest
 * code legitimately references trial DTOs (MotionSample, FlowHintIntent, step views).
 */
final class NoGameplayImportsFromHostSeamsTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES =
            List.of("import io.github.legendaryforge.legendary.mod.stormseeker.trial.");

    @Test
    void integrationLayerMustNotImportTrialInternals() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir"));

        List<Path> roots = List.of(
                repoRoot.resolve("src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/integration"),
                repoRoot.resolve("src/main/java/io/github/legendaryforge/legendary/mod/stormseeker"));

        List<Path> javaFiles = new ArrayList<>();
        for (Path r : roots) {
            if (!Files.exists(r)) {
                continue;
            }
            try (Stream<Path> st = Files.walk(r)) {
                st.filter(f -> f.toString().endsWith(".java")).forEach(javaFiles::add);
            }
        }

        assertFalse(javaFiles.isEmpty(), "Expected at least one integration-layer java file to be scanned");

        for (Path f : javaFiles) {
            String path = f.toString().replace('\\', '/');

            // Only enforce for integration + wiring; ignore unrelated stormseeker package files.
            boolean isIntegration = path.contains("/stormseeker/integration/");
            boolean isWiring = path.endsWith("/stormseeker/StormseekerWiring.java");
            if (!isIntegration && !isWiring) {
                continue;
            }

            String src = Files.readString(f);

            for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                assertFalse(
                        src.contains(prefix),
                        "Integration layer must not import trial internals (" + prefix + "): " + f);
            }
        }
    }
}

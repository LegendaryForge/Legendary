package io.github.legendaryforge.legendary.quests.stormseeker.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.quests.stormseeker.StormseekerWiring;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class SingleEngineTickEntrypointTest {

    @Test
    void exactlyOnePublicEngineTickEntrypointExists() {
        var entrypoints = Arrays.stream(StormseekerWiring.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().equals("tick"))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> m.getParameterTypes()[0].equals(StormseekerHostRuntime.class))
                .toList();

        assertEquals(
                1,
                entrypoints.size(),
                "Expected exactly one public tick(StormseekerHostRuntime) entrypoint, found: " + entrypoints);
    }
}

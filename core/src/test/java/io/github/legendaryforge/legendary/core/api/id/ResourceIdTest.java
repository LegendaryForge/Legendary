package io.github.legendaryforge.legendary.core.api.id;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResourceIdTest {

    @Test
    void parse_valid() {
        ResourceId id = ResourceId.parse("legendarycore:encounter/private_arena");
        assertEquals("legendarycore", id.namespace());
        assertEquals("encounter/private_arena", id.path());
        assertEquals("legendarycore:encounter/private_arena", id.toString());
    }

    @Test
    void parse_invalid_missingColon() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("legendarycore"));
    }

    @Test
    void parse_invalid_multipleColons() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("a:b:c"));
    }

    @Test
    void rejects_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("LegendaryCore:encounter"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("legendarycore", "Encounter"));
    }

    @Test
    void child_appends_path() {
        ResourceId base = ResourceId.parse("legendarycore:encounter");
        ResourceId child = base.child("private_arena");
        assertEquals("legendarycore:encounter/private_arena", child.toString());
    }
}

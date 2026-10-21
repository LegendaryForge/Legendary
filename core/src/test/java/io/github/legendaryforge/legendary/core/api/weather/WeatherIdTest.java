package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherIdTest {

    @Test
    void holds_its_value() {
        assertEquals("Zone1_Rain", new WeatherId("Zone1_Rain").value());
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> new WeatherId(null));
    }

    @Test
    void rejects_blank() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherId("   "));
    }

    @Test
    void equality_is_by_value() {
        assertEquals(new WeatherId("Zone1_Rain"), new WeatherId("Zone1_Rain"));
        assertNotEquals(new WeatherId("Zone1_Rain"), new WeatherId("Zone1_Cloudy_Medium"));
    }
}

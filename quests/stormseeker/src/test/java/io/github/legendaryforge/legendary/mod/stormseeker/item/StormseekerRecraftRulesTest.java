package io.github.legendaryforge.legendary.mod.stormseeker.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StormseekerRecraftRulesTest {

    @Test
    void frameRecraftAllowedOnlyIfMissing() {
        assertTrue(StormseekerRecraftRules.canRecraftFrame(false));
        assertFalse(StormseekerRecraftRules.canRecraftFrame(true));
    }

    @Test
    void weaponRecraftAllowedOnlyIfMissing() {
        assertTrue(StormseekerRecraftRules.canRecraftStormseeker(false));
        assertFalse(StormseekerRecraftRules.canRecraftStormseeker(true));
    }

    @Test
    void uniquenessHelpersMirrorEligibility() {
        assertTrue(StormseekerRecraftRules.allowsNewFrameInstance(false));
        assertFalse(StormseekerRecraftRules.allowsNewFrameInstance(true));

        assertTrue(StormseekerRecraftRules.allowsNewStormseekerInstance(false));
        assertFalse(StormseekerRecraftRules.allowsNewStormseekerInstance(true));
    }
}

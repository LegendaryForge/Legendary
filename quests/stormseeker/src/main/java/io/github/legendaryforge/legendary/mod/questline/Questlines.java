package io.github.legendaryforge.legendary.mod.questline;

import io.github.legendaryforge.legendary.core.api.questline.QuestlineModule;
import java.util.List;

/** Central registry of questlines shipped in Legendary. */
public final class Questlines {

    private static final List<QuestlineModule> ALL = List.of(new StormseekerQuestline());

    private Questlines() {
        // static utility
    }

    public static List<QuestlineModule> all() {
        return ALL;
    }
}

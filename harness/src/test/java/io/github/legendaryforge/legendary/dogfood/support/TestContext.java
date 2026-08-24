package io.github.legendaryforge.legendary.dogfood.support;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class TestContext implements EncounterContext {

    private final EncounterAnchor anchor;
    private final Map<String, Object> metadata;

    public TestContext(EncounterAnchor anchor, Map<String, Object> metadata) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.metadata = Collections.unmodifiableMap(Objects.requireNonNull(metadata, "metadata"));
    }

    @Override
    public EncounterAnchor anchor() {
        return anchor;
    }

    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }
}

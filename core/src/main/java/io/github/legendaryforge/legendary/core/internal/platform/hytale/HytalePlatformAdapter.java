package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Objects;

/**
 * Hytale platform adapter scaffold.
 *
 * <p>This class exists to define the integration seam between the Hytale runtime and LegendaryCore.
 * It intentionally contains no Hytale imports and no gameplay logic. Platform-specific wiring will
 * be introduced in a later dedicated adapter phase.</p>
 *
 * <p>Core defines mechanisms, not policy. Platforms adapt to core, never the reverse.</p>
 */
public final class HytalePlatformAdapter {

    private final CoreRuntime runtime;

    /**
     * Constructs an adapter using the default, platform-agnostic core runtime wiring.
     */
    public HytalePlatformAdapter() {
        this(new DefaultCoreRuntime(), HytaleSignalSource.noop());
    }

    /**
     * Constructs an adapter delegating to the provided core runtime.
     */
    public HytalePlatformAdapter(CoreRuntime runtime) {
        this(runtime, HytaleSignalSource.noop());
    }

    /**
     * Constructs an adapter delegating to the provided core runtime and binds platform signal sources.
     */
    public HytalePlatformAdapter(CoreRuntime runtime, HytaleSignalSource signalSource) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(signalSource, "signalSource").bind(this.runtime);
    }

    /**
     * Returns the core runtime this adapter delegates to.
     */
    public CoreRuntime runtime() {
        return runtime;
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.logic;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Phase 1: The Trek with a strict 'Leave No Trace' policy.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Initial Crater & Trek Breadcrumbs: Places 'scorched_earth' blocks.</li>
 *   <li>State Capture: Stores original BlockState and Location before modification.</li>
 *   <li>Smart Placement: Uses raycasting to snap to the highest natural ground level.</li>
 *   <li>Atmospheric Guidance: Spawns 'Distant Lightning' VFX at the Resonator destination.</li>
 *   <li>Restoration Cleanup: Restores blocks on weather change.</li>
 * </ul>
 */
public class StormseekerTrekSystem {

    // Placeholder types for platform-specific concepts until we have the actual Hytale/Platform API.
    public record Vector3d(double x, double y, double z) {}

    public record BlockPos(int x, int y, int z) {}

    public record BlockState(String id) {}

    public record WeatherChangedEvent(String newWeather) {}

    public record Resonator(Vector3d position) {}

    public record Path(List<Vector3d> points, double cost) {}

    // Placeholder World interface
    public interface World {
        BlockPos getHighestBlockAt(int x, int z);

        BlockState getBlockState(BlockPos pos);

        void setBlockState(BlockPos pos, BlockState state);

        void spawnParticle(String particle, double x, double y, double z);

        List<Resonator> getNearbyResonators(Vector3d center, double radius);

        Path calculatePath(Vector3d start, Vector3d end, Set<String> avoidBlocks);
    }

    // Session Independence: Map persists based on Storm status, not player login.
    // Using ConcurrentHashMap for thread safety if accessed by multiple systems.
    private final Map<BlockPos, BlockState> originalStates = new ConcurrentHashMap<>();
    private final EventBus eventBus;
    private final World world; // Injected dependency

    private static final Set<String> NATURAL_BLOCKS = Set.of("grass_block", "dirt", "sand", "stone", "snow");

    // Treat 'Artificial' blocks as high-cost obstacles
    private static final Set<String> ARTIFICIAL_BLOCKS = Set.of("stone_bricks", "planks", "cobblestone", "wall");

    public StormseekerTrekSystem(EventBus eventBus, World world) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.world = world; // nullable until host provides a real World implementation
        // eventBus.subscribe(WeatherChangedEvent.class, this::onWeatherChanged); // Uncomment when event exists
    }

    public void tick(StormseekerHostRuntime host) {
        if (world == null) {
            return;
        }
        // Placeholder tick logic for Phase 1
    }

    /**
     * Places the initial crater at the start of the trek.
     */
    public void placeInitialCrater(Vector3d center) {
        placeScorchedEarth(center);
    }

    /**
     * Finds the optimal resonator destination.
     * Prioritizes paths with mostly 'Walkable' terrain, avoiding extensive swimming.
     */
    public Resonator findOptimalResonator(Vector3d start) {
        List<Resonator> resonators = world.getNearbyResonators(start, 500.0); // Search radius
        Resonator bestResonator = null;
        double lowestCost = Double.MAX_VALUE;

        for (Resonator resonator : resonators) {
            Path path = world.calculatePath(start, resonator.position(), ARTIFICIAL_BLOCKS);
            if (path != null && path.cost() < lowestCost) {
                lowestCost = path.cost();
                bestResonator = resonator;
            }
        }
        return bestResonator;
    }

    /**
     * Places breadcrumbs every 15-25 blocks along the calculated path.
     */
    public void placeBreadcrumbs(Vector3d start, Resonator destination) {
        if (destination == null) return;

        Path path = world.calculatePath(start, destination.position(), ARTIFICIAL_BLOCKS);
        if (path == null || path.points().isEmpty()) return;

        double distanceAccumulator = 0;
        Vector3d lastPoint = path.points().get(0);

        for (Vector3d point : path.points()) {
            double dist = distance(lastPoint, point);
            distanceAccumulator += dist;

            // Interval Placement: 15-25 blocks (using 20 as average)
            if (distanceAccumulator >= 20.0) {
                placeScorchedEarth(point);
                distanceAccumulator = 0;
            }
            lastPoint = point;
        }

        spawnDistantLightning(destination.position());
    }

    private double distance(Vector3d a, Vector3d b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void placeScorchedEarth(Vector3d location) {
        BlockPos ground = snapToGround(location);
        if (ground != null) {
            BlockState original = world.getBlockState(ground);
            if (isNaturalBlock(original)) {
                captureState(ground, original);
                world.setBlockState(ground, new BlockState("scorched_earth"));
            }
        }
    }

    private BlockPos snapToGround(Vector3d location) {
        return world.getHighestBlockAt((int) location.x(), (int) location.z());
    }

    private boolean isNaturalBlock(BlockState state) {
        return NATURAL_BLOCKS.contains(state.id());
    }

    private void captureState(BlockPos pos, BlockState state) {
        // Only store if not already stored to preserve the *original* state
        originalStates.putIfAbsent(pos, state);
    }

    private void spawnDistantLightning(Vector3d destination) {
        BlockPos pos = snapToGround(destination);
        if (pos != null) {
            world.spawnParticle("distant_lightning", pos.x(), pos.y() + 1.0, pos.z());
        }
    }

    public void onWeatherChanged(WeatherChangedEvent event) {
        // Reliable Cleanup: Restore all blocks when storm ends.
        // Logic to check if storm ended would go here.
        // For now, assuming this event means "storm ended".
        restoreAll();
    }

    private void restoreAll() {
        for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
        originalStates.clear();
    }
}

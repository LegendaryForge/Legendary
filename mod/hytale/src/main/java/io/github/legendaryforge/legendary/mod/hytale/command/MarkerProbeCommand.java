package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerIconSize;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.DiscoverableMapMarkers;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import java.util.Map;
import org.joml.Vector3d;

/**
 * Debug command: {@code /markerprobe}
 *
 * <p>Runs the probe named in §5.4 of
 * {@code docs/superpowers/specs/2026-10-20-hytale-0.7.2-capability-assessment-design.md}, which
 * gates rank 4 — adopting world map markers as the {@code DiscoveryPublisher} port. §5.4 words the
 * probe as "publish a marker programmatically at a known position and confirm it appears, and that
 * {@code DiscoverableMapMarkers} can gate it on discovery rather than showing it from world start."
 *
 * <h2>What the jar says, so the probe can be read against it</h2>
 *
 * <p>Every signature below was read off {@code HytaleServer.jar} with {@code javap}, not inferred
 * from naming. That is deliberate: the rank-1 session produced three wrong engine signatures in one
 * file from plausible-shape reasoning, and writing them into a plan laundered them into something
 * that read as settled.
 *
 * <ul>
 *   <li>{@code DiscoverableMapMarkers} is <b>all static</b> and <b>per-player</b>:
 *       {@code isRevealed(Player, String)}, {@code reveal(Player, String)},
 *       {@code hide(Player, String)}. So the discovery gate keys on (player, markerId) — which is
 *       exactly §5.4's port shape, "this feature at P has become known to player X".
 *   <li>Publication has <b>two</b> routes. Push is
 *       {@code MapMarkerTracker.sendMapMarker(MapMarker)} — but {@code MapMarkerTracker} is not
 *       reachable from public API ({@code WorldMapTracker} exposes neither it nor a send), so this
 *       probe does not use it. Pull is
 *       {@code WorldMapManager.addMarkerProvider(String, MarkerProvider)}, where
 *       {@code MarkerProvider.update(World, Player, MarkersCollector)} is invoked per player per
 *       map tick. That is how the engine publishes its own POIs
 *       ({@code POIMarkerProvider.INSTANCE}, {@code DeathMarkerProvider}), so it is both the
 *       probe route and the production route.
 *   <li>{@code MapMarkerBuilder(String, String, Transform)} is {@code (id, image, transform)} —
 *       confirmed from the constructor's bytecode, which assigns arg1 to {@code id} and arg2 to
 *       {@code image}, and defaults {@code iconSize} to {@code Default}. Getting those two
 *       backwards would have made this probe test nothing while still passing.
 *   <li>The {@code image} is a <b>filename with extension</b>. {@code DeathMarkerProvider} passes
 *       the literal {@code "Death.png"}. Not a bare id, not a path.
 * </ul>
 *
 * <h2>Four deliberate choices, each removing a confound</h2>
 *
 * <ol>
 *   <li><b>The absent case is tested first and alone.</b> {@code isRevealed} is asserted
 *       {@code false} before anything is revealed. A negative among positives is ambiguous; a
 *       negative on its own is not.
 *   <li><b>A per-id control.</b> After revealing {@code MARKER_ID}, {@code CONTROL_ID} — never
 *       revealed, never published — must still read {@code false}. Without it, a
 *       {@code isRevealed → true} proves only that the method returns true, not that the gate keys
 *       on the id. This is the instrument control that makes the other assertions evidence.
 *   <li><b>{@code addIgnoreViewDistance}, not {@code add}.</b> {@code MarkersCollector} offers
 *       both. With {@code add}, a marker that fails to appear is ambiguous between "the mechanism
 *       does not work" and "the marker was outside view distance" — and the ambiguous half is the
 *       one we would be trying to measure.
 *   <li><b>{@code withCustomName}, not {@code withName(Message)}.</b> The engine's own providers
 *       use a {@code Message} translation key ({@code server.map.markers.death}). Shipping a key
 *       would oblige {@code checkAssetPackIntegrity} to see a Stormseeker-scoped prefix, which is
 *       a real constraint on production code and pure noise for a probe. {@code "Campfire.png"} is
 *       likewise a base-game icon chosen so nothing has to ship, and chosen to look nothing like
 *       residue — the rank-1 session recorded a probe whose instrument control was visually
 *       identical to the observable it validated, which corrupted the human reading while leaving
 *       the server-side reading intact.
 * </ol>
 *
 * <h2>Two-phase by necessity</h2>
 *
 * <p>{@code WorldMapManager} is a {@code TickingThread}, so a registered provider fires
 * <em>asynchronously</em>. This command therefore cannot observe its own publication: on the first
 * invocation {@code getSentMarkers()} will normally <b>not</b> contain the marker, and that is the
 * expected result rather than a failure. Re-issue after a few seconds — or open the map — and the
 * read-back section answers whether the marker reached the client. The verdict line reflects only
 * what this invocation could actually decide, which is why publication is reported and not graded.
 *
 * <p>The provider is left registered. There is no {@code removeMarkerProvider} in the public API,
 * so it persists until the server restarts; registration is by key, so re-running replaces rather
 * than accumulates. The marker is also left <b>revealed</b> at the end, because the one question a
 * headless run can never answer is whether it draws.
 *
 * <h2>RESULTS — run 2026-10-20, client attached</h2>
 *
 * <p>Everything §5.4 asked, answered: the gate passed 7/7 on a clean player, publication was
 * confirmed by the two-phase read-back (run 1 {@code contains = false} with 3 markers, run 2
 * {@code contains = true} with 4), and the marker <b>rendered</b> at the published position with its
 * custom name. The two-phase shape was load bearing — a single invocation would have recorded this
 * mechanism as broken.
 *
 * <p>One engine fact worth carrying into the port: <b>{@code reveal()} reports state change, not
 * success.</b> Called on an already-revealed marker it returns {@code false}. A port treating the
 * return as "did this succeed" would read an already-discovered feature as a publication failure.
 * Same shape as §5.1's {@code tryClaimOverride} correction, found the same way — by calling it twice.
 *
 * <h2>Two defects that first run exposed in THIS probe</h2>
 *
 * <p>Both are fixed, and both are worth stating because they are the probe misreporting rather than
 * the engine misbehaving — the failure mode a probe is least able to detect in itself.
 *
 * <ol>
 *   <li><b>It graded its own non-idempotence as an engine failure.</b> Choice 1 above (absent case
 *       first) and the leave-revealed decision in the previous paragraph are mutually exclusive
 *       across two runs, and the driver runs this twice by design. Run 2 therefore reported two
 *       FAILs on a mechanism working perfectly. There is now an idempotence guard: an
 *       already-revealed marker at entry means a prior run, and the gate sequence is <b>skipped
 *       with its reason stated</b> rather than failed.
 *   <li><b>Its assertion details restated the expectation, not the observation.</b> Run 2 printed
 *       {@code FAIL  not revealed before any reveal} above the detail
 *       {@code isRevealed(...) == false at world start} — on a call that had returned {@code true}.
 *       A line that contradicts the verdict beside it is worse than no detail at all. Every detail
 *       now interpolates the observed value.
 * </ol>
 */
public class MarkerProbeCommand extends AbstractWorldCommand {

    /** Published by our provider, and the subject of the reveal/hide sequence. */
    private static final String MARKER_ID = "legendary_markerprobe";

    /** Never revealed, never published. Proves the discovery gate keys on the id. */
    private static final String CONTROL_ID = "legendary_markerprobe_control";

    /** Base-game icon, so the probe ships no asset. Filename with extension — see class javadoc. */
    private static final String ICON = "Campfire.png";

    /** Provider registration key. Stable, so re-running replaces rather than accumulates. */
    private static final String PROVIDER_KEY = "legendary_markerprobe";

    /** Blocks north/up of the player, so the marker is findable but not underfoot. */
    private static final double OFFSET_XZ = 24.0;

    private boolean failed;

    public MarkerProbeCommand() {
        super("markerprobe", "Tests per-player map-marker discovery gating and provider publication");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        failed = false;

        sb.append("--- world map marker probe (0.7.2) ---\n");
        sb.append("world: ").append(world.getName()).append("\n");

        WorldMapManager mapManager = world.getWorldMapManager();
        if (mapManager == null) {
            sb.append("WorldMapManager: ABSENT -- nothing to probe\n");
            sb.append("MARKER_PROBE_VERDICT: SKIPPED | WorldMapManager absent\n");
            sendText(context, sb.toString());
            return;
        }

        sb.append("worldMapEnabled: ").append(mapManager.isWorldMapEnabled()).append("\n");
        sb.append("existing POIs: ")
                .append(mapManager.getPointsOfInterest().size())
                .append("\n");
        sb.append("existing marker providers: ")
                .append(mapManager.getMarkerProviders().keySet())
                .append("\n");

        if (!mapManager.isWorldMapEnabled()) {
            sb.append("\nThe world map is DISABLED for this world, so publication is unanswerable.\n");
            sb.append("The discovery-gate tests below do not depend on it and still run.\n");
        }

        var playerRefs = world.getPlayerRefs();
        sb.append("players: ").append(playerRefs.size()).append("\n");
        if (playerRefs.isEmpty()) {
            sb.append("\nNo connected player, so NOTHING here is answerable.\n");
            sb.append("Both halves of this probe take a Player: the discovery gate is keyed on one,\n");
            sb.append("and a MarkerProvider is invoked per player per map tick. A headless server has\n");
            sb.append("neither. This is not a degraded run -- it is no run.\n");
            sb.append("Connect a client (Direct Connect 127.0.0.1:5520) and re-issue.\n");
            sb.append("MARKER_PROBE_VERDICT: SKIPPED | no connected player\n");
            sendText(context, sb.toString());
            return;
        }

        Vector3d origin = playerOrigin(world, store);
        if (origin == null) {
            sb.append("\nNo player has a TransformComponent -- cannot site the marker.\n");
            sb.append("MARKER_PROBE_VERDICT: SKIPPED | no player transform\n");
            sendText(context, sb.toString());
            return;
        }

        Vector3d markerPos = new Vector3d(origin.x + OFFSET_XZ, origin.y, origin.z);
        sb.append("marker position: ")
                .append(fmt(markerPos))
                .append("  (player ")
                .append(fmt(origin))
                .append(" + ")
                .append(OFFSET_XZ)
                .append(" on +x)\n");

        MapMarker marker = buildMarker(sb, markerPos);
        registerProvider(sb, mapManager, marker);

        for (PlayerRef playerRef : playerRefs) {
            probePlayer(sb, playerRef);
        }

        sb.append("\nProvider left registered and marker left REVEALED on purpose -- whether it\n");
        sb.append("DRAWS is the one question a server-side read cannot answer. Open the map.\n");
        sb.append("Re-issue in a few seconds for the publication read-back (see javadoc).\n");

        sb.append("\nMARKER_PROBE_VERDICT: ").append(failed ? "FAIL" : "PASS");
        sb.append(" | ").append(playerRefs.size()).append(" player(s)\n");
        sendText(context, sb.toString());
    }

    /** Builds the marker. Graded, because a null here invalidates everything downstream. */
    private MapMarker buildMarker(StringBuilder sb, Vector3d pos) {
        sb.append("\n=== marker construction ===\n");
        try {
            MapMarker built = new MapMarkerBuilder(MARKER_ID, ICON, new Transform(pos.x, pos.y, pos.z))
                    .withCustomName("Legendary marker probe")
                    .withIconSize(MapMarkerIconSize.Major)
                    .build();
            check(sb, "build() returns a marker", built != null, "id=" + MARKER_ID + " image=" + ICON);
            return built;
        } catch (RuntimeException e) {
            check(
                    sb,
                    "build() returns a marker",
                    false,
                    "THREW " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Registers a provider that publishes the marker to every player.
     *
     * <p>{@code addIgnoreViewDistance} rather than {@code add} — see class javadoc choice 3.
     */
    private void registerProvider(StringBuilder sb, WorldMapManager mapManager, MapMarker marker) {
        sb.append("\n=== provider registration ===\n");
        if (marker == null) {
            check(sb, "provider registered", false, "no marker to publish -- construction failed");
            return;
        }
        try {
            mapManager.addMarkerProvider(PROVIDER_KEY, (w, player, collector) -> {
                collector.addIgnoreViewDistance(marker);
            });
            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            check(
                    sb,
                    "addMarkerProvider registers under our key",
                    providers.containsKey(PROVIDER_KEY),
                    "providers now: " + providers.keySet());
        } catch (RuntimeException e) {
            check(
                    sb,
                    "addMarkerProvider registers under our key",
                    false,
                    "THREW " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The §5.4 question: can a marker be hidden at world start and revealed per player? */
    private void probePlayer(StringBuilder sb, PlayerRef playerRef) {
        sb.append("\n=== [").append(playerRef.getUsername()).append("] discovery gate ===\n");

        // getComponent(ComponentType) is deprecated on PlayerRef (§7); getComponentConcurrent is
        // the migrated form and is what StormseekerStartLineInteraction already uses.
        Player player = playerRef.getComponentConcurrent(Player.getComponentType());
        if (player == null) {
            check(sb, "Player component resolvable", false, "getComponentConcurrent returned null");
            return;
        }

        // IDEMPOTENCE GUARD. Measured 2026-10-20: this probe is run TWICE per session (the
        // publication read-back needs a map tick between runs), and run 1 deliberately leaves the
        // marker revealed so the operator can look at it. Those two choices contradict the
        // absent-case-first assertion below, and on the first live run they produced two FAILs on
        // a mechanism that was working perfectly. That is the probe reporting its own design
        // conflict as an engine defect, which is worse than reporting nothing.
        //
        // So: detect the state rather than assume it. A revealed marker at entry means a prior run
        // in this session, and the gate sequence is skipped with its reason stated.
        boolean alreadyRevealed = DiscoverableMapMarkers.isRevealed(player, MARKER_ID);
        if (alreadyRevealed) {
            sb.append("  ----  gate sequence SKIPPED, not failed\n");
            sb.append("        ")
                    .append(MARKER_ID)
                    .append(" is already revealed, so a prior run in this session left it so.\n");
            sb.append("        The absent-case assertion cannot be made twice against one player.\n");
            sb.append("        Relog, or use a fresh world, to re-run the gate sequence.\n");
            appendPublicationReadBack(sb, player);
            return;
        }

        // 1. ABSENT FIRST, ALONE. A negative among positives is ambiguous.
        //    Details below report the OBSERVED value, never the expectation. The first live run
        //    printed `FAIL ... isRevealed == false at world start` on a call that had returned
        //    true -- a line that contradicts the verdict beside it, because the string restated
        //    what was wanted instead of what happened.
        check(sb, "not revealed before any reveal", true, "isRevealed(" + MARKER_ID + ") -> false at world start");

        // 2. reveal
        boolean revealed = DiscoverableMapMarkers.reveal(player, MARKER_ID);
        check(sb, "reveal() returns true", revealed, "reveal(" + MARKER_ID + ") -> " + revealed);
        boolean nowRevealed = DiscoverableMapMarkers.isRevealed(player, MARKER_ID);
        check(sb, "revealed after reveal()", nowRevealed, "isRevealed(" + MARKER_ID + ") -> " + nowRevealed);

        // 3. THE CONTROL. Without this, "isRevealed -> true" proves only that the method
        //    returns true, not that the gate keys on the id.
        boolean controlRevealed = DiscoverableMapMarkers.isRevealed(player, CONTROL_ID);
        check(
                sb,
                "CONTROL: an unrevealed id stays false",
                !controlRevealed,
                "isRevealed(" + CONTROL_ID + ") -> " + controlRevealed + " while " + MARKER_ID + " is revealed");

        // 4. hide round-trips
        boolean hidden = DiscoverableMapMarkers.hide(player, MARKER_ID);
        check(sb, "hide() returns true", hidden, "hide(" + MARKER_ID + ") -> " + hidden);
        boolean afterHide = DiscoverableMapMarkers.isRevealed(player, MARKER_ID);
        check(sb, "not revealed after hide()", !afterHide, "isRevealed(" + MARKER_ID + ") -> " + afterHide);

        // 5. Re-reveal. §5.1's lesson: a repeat operation's semantics are worth measuring rather
        //    than assuming refusal, because tryClaimOverride's same-claimant case was an update.
        //
        //    MEASURED 2026-10-20: reveal() reports STATE CHANGE, not success. Called on an
        //    already-revealed marker it returns FALSE. Here it follows a hide(), so it does change
        //    state and returns true -- but a caller treating the return as "did this succeed"
        //    would misread an already-revealed marker as a failure. The port must not.
        boolean reRevealed = DiscoverableMapMarkers.reveal(player, MARKER_ID);
        check(
                sb,
                "re-reveal after hide returns true",
                reRevealed,
                "reveal(" + MARKER_ID + ") -> " + reRevealed + " (left revealed deliberately)");

        appendPublicationReadBack(sb, player);
    }

    /**
     * Publication read-back. NOT graded.
     *
     * <p>The provider fires on the map tick, so an absence on the first invocation of a session is
     * expected rather than a failure. Measured 2026-10-20: run 1 reported {@code contains = false}
     * with 3 markers, run 2 reported {@code contains = true} with 4. The two-phase shape is load
     * bearing — a single invocation would have recorded this mechanism as not working.
     */
    private void appendPublicationReadBack(StringBuilder sb, Player player) {
        sb.append("  ----  publication read-back (not graded -- async, see javadoc)\n");
        try {
            Map<String, MapMarker> sent = player.getWorldMapTracker().getSentMarkers();
            sb.append("        getSentMarkers(): ")
                    .append(sent.size())
                    .append(" marker(s); contains ")
                    .append(MARKER_ID)
                    .append(" = ")
                    .append(sent.containsKey(MARKER_ID))
                    .append("\n");
            sb.append("        ids: ").append(sent.keySet()).append("\n");
        } catch (RuntimeException e) {
            sb.append("        getSentMarkers() THREW ")
                    .append(e.getClass().getSimpleName())
                    .append("\n");
        }
    }

    private Vector3d playerOrigin(World world, Store<EntityStore> store) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                return new Vector3d(transform.getPosition());
            }
        }
        return null;
    }

    private static String fmt(Vector3d v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private void check(StringBuilder sb, String label, boolean ok, String detail) {
        if (!ok) {
            failed = true;
        }
        sb.append(ok ? "  PASS  " : "  FAIL  ").append(label).append("\n");
        sb.append("        ").append(detail).append("\n");
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

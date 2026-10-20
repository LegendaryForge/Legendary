package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Debug command: {@code /weatheroverrideprobe}
 *
 * <p>Probes rank 1 of
 * {@code docs/superpowers/specs/2026-10-20-hytale-0.7.2-capability-assessment-design.md} §5.1 —
 * the per-player weather override. That capability gates the storm-bound ore design in §4, which
 * chose <em>additive reveal</em> specifically because player weather is now per-player while block
 * state is world-global. So the port shape this probe constrains
 * ({@code PlayerWeatherController}) is load-bearing for Stormseeker, not incidental.
 *
 * <p><strong>Why this is a separate command rather than an extension of {@code /weatherprobe}.</strong>
 * §5.1 says "extend {@code /weatherprobe}". That was written before the mechanism was read, and the
 * two probes turn out to be opposites. {@link WeatherConditionProbeCommand} is a pure read whose
 * whole value is reproducing {@code WeatherTriggerCondition}'s own path, and its Javadoc records
 * that it is an {@link AbstractWorldCommand} <em>on purpose</em> so it runs from the console with no
 * client attached. This probe <em>mutates</em> per-player override state and cannot answer anything
 * without a connected player. Folding a stateful mutating sequence into a read-only dump would cost
 * the dump the property it was designed for.
 *
 * <h2>What the bytecode says, so the probe can be read against it</h2>
 *
 * <p>{@code WeatherTracker.tryClaimOverride(Ref, int)}:
 *
 * <pre>
 *   if (overrideOwner != null &amp;&amp; overrideOwner != claimant &amp;&amp; overrideOwner.isValid()) return false;
 *   overrideOwner = claimant; overrideWeatherIndex = index; return true;
 * </pre>
 *
 * <p>Four consequences, each of which this probe tests rather than asserts:
 *
 * <ul>
 *   <li><b>The {@code Ref} is the claimant, not the subject.</b> The subject is whichever player's
 *       {@code WeatherTracker} this is. Ownership is therefore explicit, which is what §5.1's
 *       "the port must make ownership explicit rather than hiding it" requires.
 *   <li><b>Owner comparison is {@code if_acmpeq} — reference identity, not {@code equals}.</b> A
 *       different {@code Ref} instance addressing the same entity would not be recognised as the
 *       owner. {@code PlayerRef.getReference()} returns a cached field, so that source is stable;
 *       any other way of obtaining a {@code Ref} is not. Test 1 measures this rather than trusting
 *       it, because the port's release path depends on it.
 *   <li><b>A same-claimant re-claim is an update, not a refusal</b> — the identity check jumps
 *       <em>into</em> the assignment. §5.1 says "a second claim while held is refused"; that holds
 *       only for a <em>different</em> claimant. Tests 3 and 4 separate the two cases, because a
 *       probe that tested only one of them would have confirmed the spec's wording and still
 *       described the mechanism wrongly.
 *   <li><b>A stale owner is not a deadlock.</b> The incumbent only blocks while
 *       {@code isValid()}, and {@code updateWeather} calls {@code clearOverrideWeatherIndex()}
 *       outright when the owner has gone invalid. So a disconnect cannot strand an override.
 * </ul>
 *
 * <p>{@code updateWeather} resolves in this order — <b>per-player override, then world-forced, then
 * environment</b>:
 *
 * <pre>
 *   if (overrideOwner != null &amp;&amp; !overrideOwner.isValid()) clearOverrideWeatherIndex();
 *   if (overrideWeatherIndex != 0) { sendWeatherIndex(overrideWeatherIndex); return; }
 *   int forced = resource.getForcedWeatherIndex();
 *   if (forced != 0) { sendWeatherIndex(forced); return; }
 *   updateEnvironment(...); sendWeatherIndex(resource.getWeatherIndexForEnvironment(environmentId));
 * </pre>
 *
 * <h2>Two traps that shape how this probe is built</h2>
 *
 * <p><b>{@code getWeatherIndex()} is a send-log, not a resolver.</b> It returns
 * {@code updateWeather.weatherIndex} — the last value actually <em>sent to the client</em>. So
 * claiming an override and immediately reading it back returns the <em>pre-claim</em> value, and a
 * probe that read it that way would report a working claim as broken. Test 2 asserts that stale read
 * deliberately, as a control, before driving the update.
 *
 * <p>This probe therefore calls {@code tracker.updateWeather(...)} itself after every mutation
 * rather than sleeping and hoping. {@code WEATHER_UPDATE_RATE_S} is 1.0s and
 * {@code WeatherSystem$TickingSystem} is what normally drives it; a command that blocked waiting for
 * that tick would block the thread the tick runs on. Driving it directly is both synchronous and an
 * exact reproduction of the system's own call.
 *
 * <p><b>{@code sendWeatherIndex} is a no-op when the index is unchanged.</b> It compares against the
 * cached packet field and returns without writing. So every index this probe claims is chosen to
 * <em>differ</em> from the current one — otherwise "nothing changed" and "the claim worked" produce
 * identical observations. The chosen ids are printed, not just their count.
 *
 * <h2>Index 0 is a sentinel, not a weather</h2>
 *
 * <p>The gate is {@code overrideWeatherIndex != 0}, and {@code releaseOverride} clears to 0. So
 * {@code tryClaimOverride(ref, 0)} returns <b>true</b> and has <b>no effect</b> — there is no way to
 * override a player <em>to</em> weather index 0. Test 7 confirms this, because a port that accepted
 * index 0 from a caller would silently do nothing.
 *
 * <h2>The ownership protocol is advisory</h2>
 *
 * <p>{@code setOverrideWeatherIndex(int)} and {@code clearOverrideWeatherIndex()} are public and
 * perform <b>no ownership check at all</b>. Test 8 demonstrates that an unclaimed caller can set an
 * override and that anyone can clear someone else's. The port must therefore expose claim/release
 * only and never these two, or ownership means nothing.
 *
 * <h2>Precedence over world-forced weather</h2>
 *
 * <p>Test 10 is the one carrying storm-gating, and it runs <b>only when weather is already forced</b>.
 * This probe deliberately does not force weather itself: {@code WeatherSystem$TickingSystem} skips
 * the {@code environmentWeather} refresh while forced and the refresh is gated by
 * {@code compareAndSwapHour}, so briefly forcing and restoring can leave that map stale for up to an
 * in-game hour — which would make every {@code WeatherTriggerCondition} false meanwhile. That is a
 * side effect a probe should not inflict on the world it is measuring. Run {@code /weather set <id>}
 * first and re-run this probe to cover it.
 *
 * <p>This probe restores the player's weather on the way out and reports the restored value.
 */
public class WeatherOverrideProbeCommand extends AbstractWorldCommand {

    /**
     * Transition passed to {@code sendWeatherIndex}. Zero so a client's visual lerp cannot be
     * mistaken for the server not having applied the change; the value is cosmetic only.
     */
    private static final float TRANSITION_SECONDS = 0f;

    private boolean failed;

    public WeatherOverrideProbeCommand() {
        super("weatheroverrideprobe", "Tests per-player weather override claim/release semantics");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        failed = false;

        sb.append("--- per-player weather override probe (0.7.2) ---\n");
        sb.append("world: ").append(world.getName()).append("\n");
        sb.append("WEATHER_UPDATE_RATE_S=")
                .append(WeatherResource.WEATHER_UPDATE_RATE_S)
                .append(" (this probe drives updateWeather directly, so it does not wait on it)\n");

        WeatherResource weather = store.getResource(WeatherResource.getResourceType());
        if (weather == null) {
            sb.append("WeatherResource: ABSENT -- nothing to probe\n");
            sb.append("WEATHER_OVERRIDE_VERDICT: SKIPPED | WeatherResource absent\n");
            sendText(context, sb.toString());
            return;
        }

        int forced = weather.getForcedWeatherIndex();
        sb.append("forcedWeatherIndex: ")
                .append(forced)
                .append(forced == 0 ? " (unforced)" : " (" + weatherName(forced) + " -- FORCED)")
                .append("\n");

        var playerRefs = world.getPlayerRefs();
        sb.append("players: ").append(playerRefs.size()).append("\n");
        if (playerRefs.isEmpty()) {
            sb.append("\nNo connected player, so NOTHING here is answerable.\n");
            sb.append("The subject of an override is a player's WeatherTracker component, and the\n");
            sb.append("observable is a packet sent to that player's connection. A headless server has\n");
            sb.append("neither. This is not a degraded run -- it is no run.\n");
            sb.append("Connect a client (Direct Connect 127.0.0.1:5520) and re-issue.\n");
            sb.append("WEATHER_OVERRIDE_VERDICT: SKIPPED | no connected player\n");
            sendText(context, sb.toString());
            return;
        }

        for (PlayerRef playerRef : playerRefs) {
            probePlayer(sb, store, weather, playerRef);
        }

        sb.append("\nWEATHER_OVERRIDE_VERDICT: ").append(failed ? "FAIL" : "PASS");
        sb.append(" | ").append(playerRefs.size()).append(" player(s)\n");
        sendText(context, sb.toString());
    }

    private void probePlayer(StringBuilder sb, Store<EntityStore> store, WeatherResource weather, PlayerRef playerRef) {
        sb.append("\n=== [").append(playerRef.getUsername()).append("] ===\n");

        Ref<EntityStore> self = playerRef.getReference();
        if (self == null) {
            sb.append("getReference() null -- entity absent or invalid; cannot probe this player\n");
            fail();
            return;
        }

        WeatherTracker tracker = store.getComponent(self, WeatherTracker.getComponentType());
        if (tracker == null) {
            sb.append("WeatherTracker ABSENT -- not a weather-tracked entity\n");
            fail();
            return;
        }

        // Nullable: resolveUnoverriddenWeatherIndex tolerates a null transform (it skips
        // updateEnvironment and reuses the last environmentId), so this is reported, not fatal.
        TransformComponent transform = store.getComponent(self, TransformComponent.getComponentType());
        if (transform == null) {
            sb.append("note: TransformComponent ABSENT -- environment resolution will reuse the last\n");
            sb.append("      environmentId rather than the player's current position\n");
        }

        // A distinct Ref instance, used two ways: as a rival claimant (whose own validity the
        // claim check never consults), and as a deliberately INVALID incumbent for the self-heal
        // test. new Ref<>(store) sets index = Integer.MIN_VALUE, and isValid() is index != MIN_VALUE.
        Ref<EntityStore> rival = new Ref<>(store);

        // --- baseline -------------------------------------------------------------------
        drive(tracker, playerRef, weather, transform, store);
        int baseline = tracker.getWeatherIndex();
        int envId = tracker.getEnvironmentId();
        int unoverridden = tracker.resolveUnoverriddenWeatherIndex(weather, transform, store);
        int effective = weather.getEffectiveWeatherIndex(envId);

        sb.append("baseline getWeatherIndex(): ").append(describe(baseline)).append("\n");
        sb.append("environmentId: ").append(envId).append("\n");
        sb.append("resolveUnoverriddenWeatherIndex(): ")
                .append(describe(unoverridden))
                .append("\n");
        sb.append("getEffectiveWeatherIndex(env ")
                .append(envId)
                .append("): ")
                .append(describe(effective))
                .append("\n");

        int[] targets = pickTargets(baseline, weather.getForcedWeatherIndex());
        if (targets == null) {
            sb.append("\nFewer than two usable weather assets differing from the current index.\n");
            sb.append("Cannot distinguish 'override applied' from 'nothing changed', because\n");
            sb.append("sendWeatherIndex is a no-op on an unchanged index. Aborting this player.\n");
            fail();
            return;
        }
        int targetA = targets[0];
        int targetB = targets[1];
        sb.append("claim targets: A=")
                .append(describe(targetA))
                .append("  B=")
                .append(describe(targetB))
                .append("  (both differ from the current index, so a no-op cannot read as success)\n\n");

        // --- 1. claimant Ref identity --------------------------------------------------
        Ref<EntityStore> again = playerRef.getReference();
        check(
                sb,
                "1. getReference() is a stable instance",
                self == again,
                self == again
                        ? "same instance -- a port may re-derive the claimant Ref and still release"
                        : "DIFFERENT instances -- ownership is identity-compared, so the port MUST store"
                                + " the exact Ref it claimed with");

        // --- 2. claim takes, and getWeatherIndex() is a send-log -----------------------
        boolean claimed = tracker.tryClaimOverride(self, targetA);
        int immediate = tracker.getWeatherIndex();
        check(sb, "2a. tryClaimOverride(self, A)", claimed, "returned " + claimed + ", expected true");
        check(
                sb,
                "2b. getWeatherIndex() is stale before the update is driven",
                immediate == baseline,
                "read " + describe(immediate) + ", expected the PRE-claim " + describe(baseline)
                        + " -- this is the control for the send-log semantics, not a failure of the claim");
        drive(tracker, playerRef, weather, transform, store);
        check(
                sb,
                "2c. after driving updateWeather, the override is what is sent",
                tracker.getWeatherIndex() == targetA,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(targetA));

        // --- 3. a DIFFERENT claimant is refused while the owner is valid ---------------
        boolean rivalClaim = tracker.tryClaimOverride(rival, targetB);
        drive(tracker, playerRef, weather, transform, store);
        check(
                sb,
                "3a. tryClaimOverride(rival, B) while held",
                !rivalClaim,
                "returned " + rivalClaim + ", expected false");
        check(
                sb,
                "3b. the rival's index was not applied",
                tracker.getWeatherIndex() == targetA,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected the owner's " + describe(targetA));

        // --- 4. the SAME claimant re-claims as an update, not a refusal ----------------
        boolean reclaim = tracker.tryClaimOverride(self, targetB);
        drive(tracker, playerRef, weather, transform, store);
        check(
                sb,
                "4a. tryClaimOverride(self, B) while self holds",
                reclaim,
                "returned " + reclaim + ", expected true -- the identity check jumps INTO the assignment,"
                        + " so this is an update; §5.1's 'a second claim is refused' holds only for a"
                        + " different claimant");
        check(
                sb,
                "4b. the re-claim replaced the index",
                tracker.getWeatherIndex() == targetB,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(targetB));

        // --- 5. release by a non-owner is refused --------------------------------------
        boolean rivalRelease = tracker.releaseOverride(rival);
        drive(tracker, playerRef, weather, transform, store);
        check(sb, "5a. releaseOverride(rival)", !rivalRelease, "returned " + rivalRelease + ", expected false");
        check(
                sb,
                "5b. the override survived the foreign release",
                tracker.getWeatherIndex() == targetB,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(targetB));

        // --- 6. release by the owner restores ------------------------------------------
        boolean released = tracker.releaseOverride(self);
        drive(tracker, playerRef, weather, transform, store);
        int expectRestored = expectedUnoverridden(tracker, weather, transform, store);
        check(sb, "6a. releaseOverride(self)", released, "returned " + released + ", expected true");
        check(
                sb,
                "6b. release restores the unoverridden weather",
                tracker.getWeatherIndex() == expectRestored,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(expectRestored));

        // --- 7. index 0 is a sentinel, not a weather -----------------------------------
        boolean claimZero = tracker.tryClaimOverride(self, 0);
        drive(tracker, playerRef, weather, transform, store);
        int expectAfterZero = expectedUnoverridden(tracker, weather, transform, store);
        check(
                sb,
                "7a. tryClaimOverride(self, 0) returns true",
                claimZero,
                "returned " + claimZero + ", expected true -- it takes the claim, which is the trap");
        check(
                sb,
                "7b. ...and has no effect, because the gate is overrideWeatherIndex != 0",
                tracker.getWeatherIndex() == expectAfterZero,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected the unoverridden "
                        + describe(expectAfterZero) + " -- a port accepting index 0 would silently do nothing");
        tracker.releaseOverride(self);

        // --- 8. the unchecked setters bypass ownership entirely ------------------------
        tracker.setOverrideWeatherIndex(targetA);
        drive(tracker, playerRef, weather, transform, store);
        check(
                sb,
                "8a. setOverrideWeatherIndex() applies with no claim at all",
                tracker.getWeatherIndex() == targetA,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(targetA)
                        + " -- so the claim protocol is advisory; the port must not expose this");
        tracker.clearOverrideWeatherIndex();
        drive(tracker, playerRef, weather, transform, store);
        int expectAfterClear = expectedUnoverridden(tracker, weather, transform, store);
        check(
                sb,
                "8b. clearOverrideWeatherIndex() clears without owning",
                tracker.getWeatherIndex() == expectAfterClear,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(expectAfterClear));

        // --- 9. a stale owner cannot strand an override --------------------------------
        boolean staleClaim = tracker.tryClaimOverride(rival, targetA);
        check(
                sb,
                "9a. an INVALID Ref can take the claim (isValid=" + rival.isValid() + ")",
                staleClaim,
                "returned " + staleClaim + ", expected true -- nothing validates the incoming claimant");
        drive(tracker, playerRef, weather, transform, store);
        int expectSelfHealed = expectedUnoverridden(tracker, weather, transform, store);
        check(
                sb,
                "9b. updateWeather self-heals a stale owner instead of applying its index",
                tracker.getWeatherIndex() == expectSelfHealed,
                "sent " + describe(tracker.getWeatherIndex()) + ", expected the unoverridden "
                        + describe(expectSelfHealed) + " -- a disconnect cannot strand an override");
        tracker.tryClaimOverride(rival, targetA);
        boolean stealFromStale = tracker.tryClaimOverride(self, targetB);
        check(
                sb,
                "9c. a valid claimant takes over from an invalid incumbent",
                stealFromStale,
                "returned " + stealFromStale + ", expected true");
        tracker.releaseOverride(self);

        // --- 10. precedence over world-forced weather ----------------------------------
        int forcedNow = weather.getForcedWeatherIndex();
        if (forcedNow == 0) {
            sb.append("10. SKIPPED -- weather is not forced, so precedence over a world force is\n");
            sb.append("    untested. This probe will not force weather itself: TickingSystem skips the\n");
            sb.append("    environmentWeather refresh while forced and the refresh is gated by\n");
            sb.append("    compareAndSwapHour, so forcing and restoring can leave that map stale for up\n");
            sb.append("    to an in-game hour and make every WeatherTriggerCondition false meanwhile.\n");
            sb.append("    Run `/weather set <id>` then re-run this probe to cover it.\n");
        } else {
            int overrideTarget = targetA != forcedNow ? targetA : targetB;
            tracker.tryClaimOverride(self, overrideTarget);
            drive(tracker, playerRef, weather, transform, store);
            check(
                    sb,
                    "10a. the per-player override outranks the world force",
                    tracker.getWeatherIndex() == overrideTarget,
                    "sent " + describe(tracker.getWeatherIndex()) + ", expected " + describe(overrideTarget)
                            + " over the forced " + describe(forcedNow));
            tracker.releaseOverride(self);
            drive(tracker, playerRef, weather, transform, store);
            check(
                    sb,
                    "10b. release falls back to the world force, not the environment",
                    tracker.getWeatherIndex() == forcedNow,
                    "sent " + describe(tracker.getWeatherIndex()) + ", expected the forced " + describe(forcedNow));
        }

        // --- restore --------------------------------------------------------------------
        tracker.releaseOverride(self);
        tracker.clearOverrideWeatherIndex();
        drive(tracker, playerRef, weather, transform, store);
        sb.append("\nrestored to ").append(describe(tracker.getWeatherIndex())).append("\n");
    }

    /**
     * Reproduces exactly what {@code WeatherSystem$TickingSystem} does per player, so the probe
     * observes the real resolution path synchronously instead of waiting on a tick it would be
     * blocking.
     */
    private static void drive(
            WeatherTracker tracker,
            PlayerRef playerRef,
            WeatherResource weather,
            TransformComponent transform,
            Store<EntityStore> store) {
        tracker.updateWeather(playerRef, weather, transform, TRANSITION_SECONDS, store);
    }

    /**
     * Recomputed at each assertion rather than cached from the baseline: the environment's weather
     * can legitimately change mid-probe, and a cached expectation would turn that into a spurious
     * failure.
     */
    private static int expectedUnoverridden(
            WeatherTracker tracker, WeatherResource weather, TransformComponent transform, Store<EntityStore> store) {
        int resolved = tracker.resolveUnoverriddenWeatherIndex(weather, transform, store);
        // sendWeatherIndex maps MIN_VALUE (environment absent from the map) to 0 on the wire, so the
        // observable is 0 rather than MIN_VALUE in that case.
        return resolved == Integer.MIN_VALUE ? 0 : resolved;
    }

    /**
     * Two distinct non-zero weather indices, both differing from {@code current} and from
     * {@code forced}. Non-zero because 0 is the no-override sentinel; differing from current because
     * {@code sendWeatherIndex} is a no-op on an unchanged index, which would make a successful
     * override indistinguishable from nothing happening.
     */
    private static int[] pickTargets(int current, int forced) {
        var map = Weather.getAssetMap();
        int first = -1;
        for (int i = 1; i < map.getNextIndex(); i++) {
            if (i == current || i == forced || map.getAsset(i) == null) {
                continue;
            }
            if (first == -1) {
                first = i;
            } else {
                return new int[] {first, i};
            }
        }
        return null;
    }

    private void check(StringBuilder sb, String label, boolean ok, String detail) {
        if (!ok) {
            failed = true;
        }
        sb.append(ok ? "  PASS  " : "  FAIL  ").append(label).append("\n");
        sb.append("        ").append(detail).append("\n");
    }

    private void fail() {
        failed = true;
    }

    private static String describe(int index) {
        if (index == Integer.MIN_VALUE) {
            return "MIN_VALUE (environment absent from the map)";
        }
        return index + " (" + weatherName(index) + ")";
    }

    private static String weatherName(int index) {
        if (index == 0) {
            return "none/sentinel";
        }
        try {
            Weather asset = Weather.getAssetMap().getAsset(index);
            return asset == null ? "?" : String.valueOf(asset.getId());
        } catch (Exception e) {
            return "?";
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

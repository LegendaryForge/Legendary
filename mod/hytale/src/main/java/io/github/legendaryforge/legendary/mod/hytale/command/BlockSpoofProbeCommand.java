package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Debug command: {@code /blockspoofprobe}
 *
 * <p>Probes a proposed mechanism for per-player material visibility: the world holds an ordinary
 * block, an <em>attuned</em> player is told it is something else, and breaking it yields the
 * something else. If it holds, it resolves a contradiction §4 of the 0.7.2 capability assessment
 * could not: the canonical questline doc requires residue cores to be invisible to casual players
 * <em>so they cannot destroy a site</em>, while §4 established that per-player block visibility does
 * not exist and that additive reveal gives up literal invisibility ("digging blind still finds it").
 *
 * <p>The proposal differs from the per-connection spoofing §4 <em>rejected</em>, and the difference
 * is the whole point. §4 rejected it because it "would desync from authoritative server state" —
 * an objection about a lie that <strong>stays</strong> a lie. Here the lie is <em>resolved at the
 * moment of interaction</em>: the player breaks what they see as ore and receives ore. Nothing
 * persists, so there is no tracking map, no restore-on-weather-change and no restart hazard — all
 * the machinery the archived block-swap plan needed.
 *
 * <p>If it holds, the three constraints that currently conflict are satisfied at once:
 *
 * <ul>
 *   <li><b>"No quest-state-dependent world spawning"</b> — satisfied literally. The world is
 *       ordinary stone for every player.
 *   <li><b>Per-player perception</b> — attuned players see ore; nobody else does.
 *   <li><b>A casual player cannot destroy a site</b> — they break stone and get stone. Protection by
 *       <em>reward</em> rather than by visibility or permission, so there is no refused interaction
 *       for a confused player to notice.
 * </ul>
 *
 * <h2>What is measurable here, and what is not</h2>
 *
 * <p>Tests 1–4 are server-side and this probe verifies them. <b>Test 5 is not measurable from the
 * server at all</b> — whether a client renders a spoofed block, and how long the illusion survives,
 * is visible only to the person looking at it. That test therefore <em>asks</em>, and says so. It is
 * also the load-bearing unknown: if the client is corrected whenever anything touches the section,
 * the whole proposal fails regardless of how tests 1–4 come out.
 *
 * <h2>Why the drop is engine-native rather than an inventory grant</h2>
 *
 * <p>{@code BlockHarvestUtils.performBlockBreak} has an overload taking the {@link BlockType}
 * <strong>as an explicit parameter</strong> rather than reading it from the world. So the engine's
 * own break routine can be invoked at a position while being told the block was our ore, and it
 * produces the drop from <em>that</em> type's {@code BlockGathering.breaking} config — correct
 * position, engine physics, engine particles and sound, a real ground item the player walks over.
 *
 * <p>That matters because the obvious shortcut is wrong. Granting the item straight into the
 * inventory would work and would feel wrong — no item pops out of the block. Test 2 exists to prove
 * the engine-native path produces a genuine <em>world entity</em>, which is why it counts entities
 * carrying {@link ItemComponent} rather than trusting that a drop happened: an inventory add spawns
 * no entity, so the count is what distinguishes the two.
 *
 * <p>Two dead ends, recorded so nobody looks for them again. {@code DropItemEvent} is cancellable
 * but has a bare no-arg constructor and <em>no accessors at all</em>, so it carries nothing to
 * inspect or modify. And block drops are asset config — {@code BlockBreakingDropType} lives on the
 * block <em>type</em> ({@code itemId}, {@code dropListId}, {@code quantity}, {@code gatherType},
 * {@code quality}) — so they cannot vary per position or per player through the asset.
 *
 * <h2>Argument shapes, read off engine call sites rather than guessed</h2>
 *
 * <p>The 11-argument {@code performBlockBreak} was read from {@code BlockHarvestUtils}' own
 * delegation and from {@code DestroyBlockInteraction}: the {@link ItemStack} may be {@code null},
 * both {@link String} parameters are passed {@code null}, and both {@code int} parameters are
 * {@code 0} on the paths inspected. They are passed here as the engine passes them rather than given
 * names this probe cannot verify.
 *
 * <p>A <b>breaker entity is required</b> and is the calling player. Headless, test 2 attempts
 * {@code null} inside a guard purely to discover whether the engine tolerates it — that result is
 * reported as exploratory, not as a pass.
 */
public class BlockSpoofProbeCommand extends AbstractWorldCommand {

    /** What the world really holds. Plain stone, from {@code Server/BlockTypeList}. */
    private static final String HOST_BLOCK = "Rock_Stone";

    /**
     * The realistic spoof: gold ore in a <em>stone</em> matrix, which is literally the design —
     * stone that is secretly ore. Visually it is stone with bright flecks.
     */
    private static final String SPOOF_REALISTIC = "Ore_Gold_Stone";

    /**
     * The sensitivity spoof, and the reason it exists: on 2026-10-20 the operator reported the
     * spoofed block "didn't look like anything else but stone -- the color seemed slightly different,
     * but I'm not sure. Maybe that block was supposed to look like that as well." A subtle spoof
     * cannot distinguish "spoofing does not work" from "spoofing works and this texture is close to
     * stone". A glowing mushroom differs in shape, colour <em>and</em> light emission, so if a stone
     * cube renders as this, spoofing unquestionably works.
     */
    private static final String SPOOF_OBVIOUS = "Plant_Crop_Mushroom_Glowing_Orange";

    /**
     * Passed as the item in hand when invoking a break. Top-tier deliberately: block drops are
     * tool-gated ({@code BlockGathering.toolData}, {@code BlockBreakingDropType.gatherType} /
     * {@code quality}), so a tool-tier penalty would be indistinguishable from the no-drop defect
     * this probe is chasing.
     */
    private static final String BREAK_TOOL = "Tool_Pickaxe_Onyxium";

    /** Used only when no player is connected; a player's own position is preferred. */
    private static final int HEADLESS_PROBE_Y = 100;

    private static final int LOAD_TIMEOUT_SECONDS = 30;

    private boolean failed;

    private int skipped;

    public BlockSpoofProbeCommand() {
        super("blockspoofprobe", "Tests per-player block spoofing and engine-native drop substitution");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        failed = false;
        skipped = 0;

        sb.append("--- per-player block spoof + drop substitution probe (0.7.2) ---\n");
        sb.append("world: ").append(world.getName()).append("\n");

        BlockType hostType = resolve(HOST_BLOCK);
        BlockType spoofType = resolve(SPOOF_REALISTIC);
        int hostId = BlockType.getAssetMap().getIndex(HOST_BLOCK);
        int spoofId = BlockType.getAssetMap().getIndex(SPOOF_REALISTIC);

        sb.append("host block:  ")
                .append(HOST_BLOCK)
                .append(hostType == null ? " -- UNRESOLVED" : " (id " + hostId + ")")
                .append("\n");
        sb.append("spoof block: ")
                .append(SPOOF_REALISTIC)
                .append(spoofType == null ? " -- UNRESOLVED" : " (id " + spoofId + ")")
                .append("\n");
        if (hostType == null || spoofType == null) {
            sb.append("\nA block id did not resolve, so nothing below can be trusted. Block ids change\n");
            sb.append("across game updates -- re-read Server/BlockTypeList from Assets.zip.\n");
            sb.append("BLOCK_SPOOF_VERDICT: FAIL | block id unresolved\n");
            sendText(context, sb.toString());
            return;
        }

        var playerRefs = world.getPlayerRefs();
        PlayerRef player = playerRefs.isEmpty() ? null : playerRefs.iterator().next();
        sb.append("players: ").append(playerRefs.size());
        sb.append(player == null ? " (headless -- tests 4 and 5 cannot run)\n" : "\n");

        Vector3i pos = choosePosition(world, store, player);
        if (pos != null) {
            runTests(context, sb, world, store, player, pos);
            return;
        }

        // Nothing resident where we wanted to write. Force a load rather than reporting SKIPPED:
        // tests 1-3 are the decisive ones (engine-native drop substitution) and they need no client,
        // so making them answerable headless is worth the asynchrony. Shape copied from
        // /beamprobe, which forces a load the same way.
        //
        // The cost, inherited and accepted: this command becomes an async chain and therefore
        // NO LONGER PRINTS WHEN IT RETURNS. The 0.7.2 removal of
        // World.waitForFutureWithoutLock is why there is no synchronous option -- the engine
        // composes instead, and blocking the world thread to wait for a chunk the world thread
        // must load would deadlock.
        final Vector3i forced = new Vector3i(0, HEADLESS_PROBE_Y, 0);
        sb.append("no resident section at the preferred position -- forcing a load at ")
                .append(forced.x)
                .append(" ")
                .append(forced.y)
                .append(" ")
                .append(forced.z)
                .append("\n");
        world.getChunkStore()
                .getChunkSectionReferenceAtBlockAsync(forced.x, forced.y, forced.z)
                .orTimeout(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAcceptAsync(
                        ref -> {
                            sb.append("  section ref ")
                                    .append(ref == null ? "null" : (ref.isValid() ? "VALID" : "invalid"))
                                    .append("\n");
                            if (ref == null || !ref.isValid()) {
                                sb.append("\nForced load produced no usable section, so no test can run.\n");
                                sb.append("BLOCK_SPOOF_VERDICT: SKIPPED | no loaded section\n");
                                sendText(context, sb.toString());
                                return;
                            }
                            runTests(context, sb, world, store, player, forced);
                        },
                        world)
                .exceptionally(t -> {
                    sb.append("force-load THREW ")
                            .append(t.getClass().getSimpleName())
                            .append(": ")
                            .append(t.getMessage())
                            .append("\n");
                    sb.append("BLOCK_SPOOF_VERDICT: FAIL | force-load failed\n");
                    sendText(context, sb.toString());
                    return null;
                });
    }

    /**
     * The tests themselves, reached either synchronously (a resident section was already available,
     * which is the client-attached case) or from the forced-load continuation.
     *
     * <p>{@code failed} and {@code skipped} are instance fields mutated here, so this command assumes
     * one invocation at a time. That holds for a debug command issued from a console and is not worth
     * synchronising; it is stated because the forced-load path runs this on the world executor rather
     * than on the caller's thread.
     */
    private void runTests(
            CommandContext context,
            StringBuilder sb,
            World world,
            Store<EntityStore> store,
            PlayerRef player,
            Vector3i pos) {
        BlockType hostType = resolve(HOST_BLOCK);
        BlockType spoofType = resolve(SPOOF_REALISTIC);
        int hostId = BlockType.getAssetMap().getIndex(HOST_BLOCK);
        int spoofId = BlockType.getAssetMap().getIndex(SPOOF_REALISTIC);

        sb.append("probe position: ")
                .append(pos.x)
                .append(" ")
                .append(pos.y)
                .append(" ")
                .append(pos.z)
                .append("\n\n");

        Ref<EntityStore> breaker = player == null ? null : player.getReference();

        // --- 1. the write path still works, and the world really holds the host block ---------
        boolean placed = place(world, pos, hostId, hostType);
        String readBack = readBlockId(world, pos);
        // Assert the POSTCONDITION, not setBlock's return. Measured 2026-10-20: at the headless
        // position the world already held Rock_Stone, so setBlock returned false as a no-op while
        // the read-back was correct -- and the original form of this check failed on a world that
        // was in exactly the state it wanted. The return value is reported as information because
        // "false" is ambiguous between no-op and refusal, and only the read-back settles it.
        check(
                sb,
                "1. the world holds the host block at the probe position",
                HOST_BLOCK.equals(readBack),
                "SectionReader reads '" + readBack + "', expected '" + HOST_BLOCK + "' (setBlock returned " + placed
                        + " -- false can mean no-op when the block was already this type)"
                        + " -- this is the control: the world is stone, not ore");

        // --- 2/3. does a break produce a drop at all, and does substitution survive it? ------
        // Revised 2026-10-20 after the first client run: with ItemStack=null, BOTH the substituted
        // break and the host-type control dropped nothing (22 -> 22) while the instrument check
        // moved (22 -> 23). So the absence was real, and the null-breaker hypothesis died with it --
        // the breaker was a real player. The surviving hypothesis is the TOOL: drops are gated by
        // BlockGathering.toolData and BlockBreakingDropType.gatherType/quality, and breaking the
        // same block BY HAND produced cobblestone. So all three calls below now pass a real pickaxe.
        //
        // Three calls, one variable at a time, because "no drop" has three possible homes:
        //   2  11-arg with a SUBSTITUTED BlockType   -- what the proposal needs
        //   3  11-arg with the HOST BlockType        -- isolates substitution from the overload
        //   REF 6-arg, engine reads its own type     -- the known-good reference path
        // If REF drops and 2/3 do not, the explicit-BlockType overload is the problem. If none drop,
        // our invocation is wrong in a way all three share. If all drop, the tool was the whole story.
        ItemStack tool = null;
        try {
            tool = new ItemStack(BREAK_TOOL, 1);
        } catch (Exception e) {
            sb.append("  note: could not construct ")
                    .append(BREAK_TOOL)
                    .append(" (")
                    .append(e.getClass().getSimpleName())
                    .append(") -- breaks will run tool-less, which is the condition that already failed\n");
        }
        sb.append("item in hand for breaks: ")
                .append(tool == null ? "NONE" : BREAK_TOOL)
                .append("\n\n");

        runBreakCase(
                sb,
                world,
                store,
                pos,
                hostId,
                hostType,
                "2",
                "substituted type " + SPOOF_REALISTIC,
                spoofType,
                tool,
                breaker,
                false);
        runBreakCase(
                sb,
                world,
                store,
                pos,
                hostId,
                hostType,
                "3",
                "HOST type " + HOST_BLOCK,
                hostType,
                tool,
                breaker,
                false);
        runBreakCase(
                sb,
                world,
                store,
                pos,
                hostId,
                hostType,
                "REF",
                "6-arg, engine reads its own type",
                null,
                tool,
                breaker,
                true);

        // --- 4. a dropped item is a real, positioned, inspectable entity ---------------------
        if (breaker == null) {
            sb.append("  SKIP  4. ground item is a real world entity -- needs a connected player\n");
            sb.append("        ItemUtils.throwItem anchors the drop to an entity, and headless there is none\n");
            skipped++;
        } else {
            try {
                // This test does double duty. It proves a position-anchored ground drop is available
                // as a fallback -- and, because throwItem RETURNS the entity's Ref, it validates the
                // counter test 2b depends on. A 0 -> 0 in test 2b is a claim about the world only if
                // the counter demonstrably moves when an item really spawns; otherwise it is a claim
                // about the instrument. Standing caution: an explanation for a null result is a
                // hypothesis about the instrument at least as often as about the world.
                int preThrow = countItemEntities(store);
                ItemStack stack = new ItemStack(SPOOF_REALISTIC, 1);
                Ref<EntityStore> dropped = ItemUtils.throwItem(breaker, store, stack, new Vector3d(0, 0.2, 0), 0.1f);
                int postThrow = countItemEntities(store);
                ItemComponent item =
                        dropped == null ? null : store.getComponent(dropped, ItemComponent.getComponentType());
                TransformComponent where =
                        dropped == null ? null : store.getComponent(dropped, TransformComponent.getComponentType());
                check(
                        sb,
                        "4b. INSTRUMENT CHECK -- the item-entity counter moves when one really spawns",
                        postThrow > preThrow,
                        "count " + preThrow + " -> " + postThrow
                                + (postThrow > preThrow
                                        ? " -- so test 2b's count is trustworthy, and a 0 -> 0 there is a real"
                                                + " absence of drops"
                                        : " -- the counter did NOT move even though an item entity was spawned,"
                                                + " so test 2b measures nothing and its result must be discarded,"
                                                + " not believed"));
                check(
                        sb,
                        "4a. ItemUtils.throwItem returns a real item entity",
                        dropped != null && item != null,
                        dropped == null
                                ? "returned null"
                                : "Ref valid=" + dropped.isValid() + ", ItemComponent="
                                        + (item == null ? "ABSENT" : "present") + ", position="
                                        + (where == null ? "unknown" : where.getPosition())
                                        + " -- so a position-anchored ground drop is available as a fallback"
                                        + " if the engine-native path misbehaves");
            } catch (Exception e) {
                sb.append("  FAIL  4. ItemUtils.throwItem threw ")
                        .append(e.getClass().getSimpleName())
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
                failed = true;
            }
        }

        // --- 5. the per-player visual spoof -- NOT measurable from here ----------------------
        // Three blocks in a row, IDENTICAL server-side, only two spoofed. The control exists
        // because of what the first run produced: "I don't think the stone block in the air looked
        // like anything else but stone. The color seemed slightly different, but I'm not sure.
        // Maybe that block was supposed to look like that as well." A lone spoofed block cannot
        // answer that -- there is nothing to compare it against. A side-by-side can.
        //
        // And two spoofs rather than one, because a realistic spoof and a sensitive one answer
        // different questions. Gold-in-stone is the design; a glowing mushroom is the instrument
        // check. If CONTROL and GOLD look alike but MUSHROOM differs, spoofing works and gold ore
        // is simply too close to stone to read.
        Vector3i control = new Vector3i(pos.x, pos.y, pos.z);
        Vector3i goldPos = new Vector3i(pos.x + 2, pos.y, pos.z);
        Vector3i obviousPos = new Vector3i(pos.x + 4, pos.y, pos.z);
        int obviousId = BlockType.getAssetMap().getIndex(SPOOF_OBVIOUS);

        boolean allPlaced = place(world, control, hostId, hostType)
                | place(world, goldPos, hostId, hostType)
                | place(world, obviousPos, hostId, hostType);
        String c = readBlockId(world, control);
        String g = readBlockId(world, goldPos);
        String o = readBlockId(world, obviousPos);
        check(
                sb,
                "5a. three IDENTICAL host blocks placed in a row",
                HOST_BLOCK.equals(c) && HOST_BLOCK.equals(g) && HOST_BLOCK.equals(o),
                "reads '" + c + "' / '" + g + "' / '" + o + "' at x=" + control.x + ", " + goldPos.x + ", "
                        + obviousPos.x + " (place() reported " + allPlaced + ") -- all three must be the SAME"
                        + " block server-side or the comparison proves nothing");

        if (player == null) {
            sb.append("  SKIP  5b. per-player visual spoof -- needs a connected client\n");
            sb.append("        ServerSetBlock is a ToClientPacket written to ONE player's handler, so with\n");
            sb.append("        no player there is nothing to write to and nobody to see it.\n");
            skipped++;
        } else if (obviousId < 0) {
            sb.append("  FAIL  5b. ").append(SPOOF_OBVIOUS).append(" did not resolve -- no sensitivity check\n");
            failed = true;
        } else {
            boolean sentGold = spoof(player, goldPos, spoofId);
            boolean sentObvious = spoof(player, obviousPos, obviousId);
            check(
                    sb,
                    "5b. both spoof packets written to " + player.getUsername(),
                    sentGold && sentObvious,
                    "gold=" + sentGold + ", obvious=" + sentObvious);

            // Re-send on a delay. The first run placed the block and spoofed it in the SAME tick,
            // and place() uses PERFORM_BLOCK_UPDATE, which broadcasts a real block update. If that
            // broadcast is queued and flushed after our direct packet write, the client receives
            // spoof-then-correction and ends on stone -- indistinguishable from a spoof that never
            // rendered. Re-sending a second later separates those two.
            try {
                java.util.concurrent.CompletableFuture.runAsync(
                        () -> {
                            spoof(player, goldPos, spoofId);
                            spoof(player, obviousPos, obviousId);
                        },
                        java.util.concurrent.CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS, world));
                sb.append("        re-send scheduled for +2s, so an ordering race against the engine's own\n");
                sb.append("        block-update broadcast cannot masquerade as a spoof that did not render\n");
            } catch (Exception e) {
                sb.append("        re-send could NOT be scheduled (")
                        .append(e.getClass().getSimpleName())
                        .append(") -- if the blocks look like stone, the ordering race is still unexcluded\n");
            }

            sb.append("\n");
            sb.append("        >>> THIS PROBE CANNOT SEE YOUR SCREEN. Three blocks in a row, all stone\n");
            sb.append("        >>> server-side. Stand so you can see all three at once:\n");
            sb.append("          x=")
                    .append(control.x)
                    .append("  CONTROL  -- not spoofed, plain ")
                    .append(HOST_BLOCK)
                    .append("\n");
            sb.append("          x=")
                    .append(goldPos.x)
                    .append("  spoofed as ")
                    .append(SPOOF_REALISTIC)
                    .append(" (the realistic case)\n");
            sb.append("          x=")
                    .append(obviousPos.x)
                    .append("  spoofed as ")
                    .append(SPOOF_OBVIOUS)
                    .append(" (unmistakable -- glows)\n");
            sb.append("        all at y=")
                    .append(pos.y)
                    .append(" z=")
                    .append(pos.z)
                    .append("\n");
            sb.append("\n");
            sb.append("        (a) Do the three look DIFFERENT from each other? That is the whole question.\n");
            sb.append("            all three identical          -> spoofing does not render. Proposal dead.\n");
            sb.append("            only the mushroom differs     -> spoofing WORKS; gold ore is too subtle.\n");
            sb.append("            gold and mushroom both differ -> spoofing works and is usable as designed.\n");
            sb.append("        (b) Walk away until it unloads, come back. Still spoofed?\n");
            sb.append("        (c) Place or break a block NEXT to a spoofed one. Does it revert?\n");
            sb.append("        (d) Relog. Still spoofed?\n");
            sb.append("        (e) Break a spoofed one. What drops, and did the break TIME feel normal?\n");
            sb.append("\n");
            sb.append("        Expect STONE from (e) today -- nothing wires BreakBlockEvent yet, so a real\n");
            sb.append("        break takes the engine's ordinary path. That is expected, not a failure.\n");
        }

        sb.append("\nleaving ")
                .append(HOST_BLOCK)
                .append(" at the probe position for inspection -- break it or ignore it, nothing persists\n");

        sb.append("\nBLOCK_SPOOF_VERDICT: ");
        if (failed) {
            sb.append("FAIL");
        } else if (skipped > 0) {
            sb.append("PARTIAL");
        } else {
            sb.append("PASS");
        }
        sb.append(" | ").append(skipped).append(" test(s) skipped");
        sb.append(player == null ? " (headless)" : " (client attached)").append("\n");
        sendText(context, sb.toString());
    }

    /**
     * One break case: re-place the host block, count item entities, break, count again.
     *
     * <p>Re-placing every time is what makes the three cases comparable — otherwise case 3 would run
     * against the hole case 2 left and measure nothing.
     *
     * @param useSixArg invoke the 6-argument overload, which reads the block type from the world
     *     instead of taking one. That is the reference path: if it drops and the explicit-type calls
     *     do not, the difference is the overload rather than our arguments.
     */
    private void runBreakCase(
            StringBuilder sb,
            World world,
            Store<EntityStore> store,
            Vector3i pos,
            int hostId,
            BlockType hostType,
            String label,
            String describe,
            BlockType type,
            ItemStack tool,
            Ref<EntityStore> breaker,
            boolean useSixArg) {
        if (!place(world, pos, hostId, hostType) && !HOST_BLOCK.equals(readBlockId(world, pos))) {
            sb.append("  FAIL  ").append(label).append(". ").append(describe).append("\n");
            sb.append("        could not re-place the host block, so this case measures nothing\n");
            failed = true;
            return;
        }
        int before = countItemEntities(store);
        String outcome = useSixArg
                ? breakReference(world, store, pos, tool, breaker)
                : breakWithType(world, store, pos, type, tool, breaker);
        int after = countItemEntities(store);
        String afterBlock = readBlockId(world, pos);

        if (outcome != null) {
            sb.append("  FAIL  ").append(label).append(". ").append(describe).append("\n");
            sb.append("        threw ").append(outcome).append("\n");
            failed = true;
            return;
        }
        boolean resident = afterBlock != null && !"NOT_RESIDENT".equals(afterBlock);
        check(
                sb,
                label + "a. break removed the block -- " + describe,
                resident && !HOST_BLOCK.equals(afterBlock),
                "block now reads '" + afterBlock + "', expected a resident section no longer holding " + HOST_BLOCK);
        check(
                sb,
                label + "b. a GROUND ITEM entity spawned -- " + describe,
                after > before,
                "ItemComponent count " + before + " -> " + after
                        + "; an inventory add spawns no entity, so a rise is what marks a real world drop");
    }

    /**
     * The reference path: the 6-argument overload, which reads the block type from the world. Shape
     * taken from {@code DestroyBlockInteraction}, which passes {@code null} for the item and {@code 0}
     * for the int.
     */
    private static String breakReference(
            World world, Store<EntityStore> store, Vector3i pos, ItemStack tool, Ref<EntityStore> breaker) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return "no loaded section at the probe position";
            }
            BlockHarvestUtils.performBlockBreak(breaker, tool, pos, sectionRef, store, chunkStore.getStore());
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * Invokes the engine's own break at a position while telling it the block was {@code type}.
     * Returns {@code null} on success, or a description of what went wrong.
     */
    private static String breakWithType(
            World world,
            Store<EntityStore> store,
            Vector3i pos,
            BlockType type,
            ItemStack tool,
            Ref<EntityStore> breaker) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return "no loaded section at the probe position";
            }
            BlockHarvestUtils.performBlockBreak(
                    pos, type, tool, 0, null, null, 0, breaker, sectionRef, store, chunkStore.getStore());
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * Entities carrying {@link ItemComponent} — i.e. items lying in the world. {@code ComponentType}
     * implements {@code Query}, so this is an exact count rather than an iteration.
     */
    private static int countItemEntities(Store<EntityStore> store) {
        try {
            return store.getEntityCountFor(ItemComponent.getComponentType());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Tells one client a position holds {@code blockId}, without touching server state. */
    private static boolean spoof(PlayerRef player, Vector3i pos, int blockId) {
        try {
            ServerSetBlock packet = new ServerSetBlock();
            packet.x = pos.x;
            packet.y = pos.y;
            packet.z = pos.z;
            packet.blockId = blockId;
            player.getPacketHandler().write(packet);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A position inside a loaded section: two blocks above the player when there is one, otherwise a
     * fixed spot near the origin. Returns null when no section is loaded, which this probe reports
     * rather than working around — forcing a chunk load is a different probe's job.
     */
    private static Vector3i choosePosition(World world, Store<EntityStore> store, PlayerRef player) {
        Vector3i candidate = null;
        if (player != null) {
            Ref<EntityStore> ref = player.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d p = transform.getPosition();
                candidate = new Vector3i((int) Math.floor(p.x) + 2, (int) Math.floor(p.y) + 1, (int) Math.floor(p.z));
            }
        }
        if (candidate == null) {
            candidate = new Vector3i(0, HEADLESS_PROBE_Y, 0);
        }
        Ref<ChunkStore> sectionRef =
                world.getChunkStore().getChunkSectionReferenceAtBlock(candidate.x, candidate.y, candidate.z);
        return sectionRef != null && sectionRef.isValid() ? candidate : null;
    }

    private static boolean place(World world, Vector3i pos, int blockId, BlockType type) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return false;
            }
            return BlockOperations.setBlock(
                    chunkStore,
                    sectionRef,
                    pos.x,
                    pos.y,
                    pos.z,
                    blockId,
                    type,
                    0,
                    0,
                    SetBlockSettings.PERFORM_BLOCK_UPDATE);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads a block id, or {@code "NOT_RESIDENT"} when the section is not in memory.
     *
     * <p>The {@code hasStorageInMemory} guard is load-bearing and must not be dropped as redundant.
     * {@code SectionReader.getBlock} returns <strong>0 for a non-resident section</strong> — it
     * neither loads nor throws — so without it an unloaded chunk is indistinguishable from air. That
     * is the fail-green defect {@code a061407} fixed after {@code 21eb7bf} introduced it, and it
     * would land here too: test 1 would read "EMPTY" and conclude the host block failed to place,
     * and test 2a would read "EMPTY" and conclude the break succeeded. Both wrong, in opposite
     * directions, from the same missing check.
     */
    private static String readBlockId(World world, Vector3i pos) {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(pos.x, pos.y, pos.z)) {
                return "NOT_RESIDENT";
            }
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x, pos.y, pos.z));
            return type == null ? null : String.valueOf(type.getId());
        } catch (Exception e) {
            return "THREW " + e.getClass().getSimpleName();
        }
    }

    private static BlockType resolve(String id) {
        try {
            int index = BlockType.getAssetMap().getIndex(id);
            return index < 0 ? null : BlockType.getAssetMap().getAsset(index);
        } catch (Exception e) {
            return null;
        }
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

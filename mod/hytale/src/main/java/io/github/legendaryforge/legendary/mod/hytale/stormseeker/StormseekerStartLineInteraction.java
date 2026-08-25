package io.github.legendaryforge.legendary.mod.hytale.stormseeker;

import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.objectives.interactions.StartObjectiveInteraction;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/**
 * Starts an objective line for the interacting player, without touching any item stack.
 *
 * <p>The shipped {@code StartObjectiveInteraction} exists to stamp an objective UUID onto a
 * quest ITEM: after resolving the interacting player it calls {@code ctx.getHeldItem()} and
 * reads metadata off the result. Fired from a block interaction chain (this mod's Act II
 * inscription signs, which start their objective line from {@code BlockType.Interactions.Use},
 * not from an item) there is no held item, so that call NPEs:
 *
 * <pre>
 * java.lang.NullPointerException: Cannot invoke
 * "...ItemStack.getFromMetadataOrNull(...)" because "itemStack" is null
 *   at ...objectives.interactions.StartObjectiveInteraction.firstRun(StartObjectiveInteraction.java:55)
 * </pre>
 *
 * <p>That is an engine constraint, not a config error — {@code StartObjective} is item-only by
 * design. This class extends it to reuse the {@code Setup} field and the player-resolution
 * steps (inherited via {@link StartObjectiveInteraction#CODEC} as this codec's parent), but
 * overrides {@link #firstRun} to call {@code objectiveTypeSetup.setup(...)} directly instead of
 * routing through an {@code ItemStack}. Every item-stamping step in the shipped
 * implementation's private {@code startObjective(...)} — writing the objective UUID into BSON
 * item metadata, {@code withMetadata}, {@code setObjectiveItemStarter}, {@code setHeldItem},
 * {@code replaceItemStackInSlot} — is skipped entirely; none of it applies when there is no
 * item to begin with.
 *
 * <p><b>Dedup guard.</b> The shipped implementation's re-click protection also lives in the
 * item path we skip: {@code startObjective(...)} stamps the objective UUID into the item's BSON
 * metadata, and a second right-click with that same item finds the UUID already present and
 * routes to {@code addPlayerToExistingObjective} instead of starting a new line. Nothing in the
 * item-free path replaces that check, so without a guard here every extra right-click on a
 * block while the line is already active calls {@code setup(...)} again. {@code
 * ObjectivePlugin.startObjectiveLine} does filter the player out via {@code
 * canPlayerDoObjectiveLine} and message them {@code playerAlreadyDoingObjectiveLine} — but it
 * does that filtering, then unconditionally calls {@code startObjective(objectiveIds[0], <empty
 * set>, ...)} regardless, producing a zero-participant {@code Objective} that is added to {@code
 * ObjectiveDataStore} and never collected. {@link #firstRun} therefore calls {@code
 * canPlayerDoObjectiveLine} itself, before {@code setup(...)}, to avoid creating that orphan in
 * the first place.
 */
public class StormseekerStartLineInteraction extends StartObjectiveInteraction {

    public static final BuilderCodec<StormseekerStartLineInteraction> CODEC = BuilderCodec.builder(
                    StormseekerStartLineInteraction.class,
                    StormseekerStartLineInteraction::new,
                    StartObjectiveInteraction.CODEC)
            .documentation("Starts the given objective line for the interacting player."
                    + " Unlike StartObjective, this does not require or touch a held item —"
                    + " use it from block interactions, not just item interactions.")
            .build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext ctx, CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = ctx.getCommandBuffer();
        Ref<EntityStore> entity = ctx.getEntity();
        PlayerRef player = cb.getComponent(entity, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }

        Player playerEntity = player.getComponent(Player.getComponentType());
        if (playerEntity == null) {
            return;
        }

        String objectiveLineId = objectiveTypeSetup.getObjectiveIdToStart();
        if (!ObjectivePlugin.get().canPlayerDoObjectiveLine(playerEntity, objectiveLineId)) {
            // Player is already doing this line (or has finished it) — a re-read of the wall
            // is normal, not an error, so return quietly without starting a second, orphaned
            // Objective. See class javadoc "Dedup guard".
            return;
        }

        Store<EntityStore> store = cb.getStore();
        World world = store.getExternalData().getWorld();

        Objective objective = objectiveTypeSetup.setup(
                Set.of(player.getUuid()), world.getWorldConfig().getUuid(), null, store);

        if (objective == null) {
            ObjectivePlugin.get()
                    .getLogger()
                    .atWarning()
                    .log(
                            "Failed to start objective line '%s' from block interaction",
                            objectiveTypeSetup.getObjectiveIdToStart());
        }
    }
}

package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2IntMap;

/**
 * Debug command: {@code /weatherprobe [--world=?]}
 *
 * <p>Dumps the exact inputs {@code WeatherTriggerCondition.isConditionMet} consumes, so an
 * asset-side weather gate can be diagnosed by reading its data source rather than by inferring
 * from whether a quest tracker appears on a client.
 *
 * <p>The condition's chain, read from {@code HytaleServer.jar} bytecode, is:
 *
 * <pre>
 *   sectionRef = transform.getSectionRef()             // false if null or invalid
 *   section    = chunkStore[sectionRef] as ChunkSection
 *   columnRef  = section.getChunkColumnReference()     // false if null or invalid
 *   env        = blockChunk.getEnvironment(pos)        // environment index at the MARKER's position
 *   index      = weatherResource.getWeatherIndexForEnvironment(env)
 *   return Arrays.binarySearch(weatherIndexes, index) >= 0
 * </pre>
 *
 * <p><strong>Changed in 0.6.1.</strong> The first hop was {@code transform.getChunkRef()} returning a
 * ref resolvable directly as a {@link BlockChunk}. 0.6.1 renamed it {@code getSectionRef()} and
 * inserted a {@link ChunkSection} between the two, so the ref must now be resolved as a section and
 * its {@code getChunkColumnReference()} followed to reach the {@code BlockChunk}. The rename alone
 * compiles; resolving the section ref as a {@code BlockChunk} would yield null and read as "player
 * is not in a loaded chunk" rather than as a mistake.
 *
 * <p>Two traps this exists to make visible. {@code environmentWeather} carries a
 * {@code defaultReturnValue} of {@link Integer#MIN_VALUE}, so an absent environment key yields a
 * value matching <em>no</em> weather id — including a list naming every weather in the game. And
 * {@code WeatherSystem$TickingSystem} branches around the block that populates that map whenever
 * weather is forced, so {@code /weather set} starves the very data source the condition reads.
 *
 * <p>This is an {@link AbstractWorldCommand} on purpose: the resource-level half needs no player,
 * so it runs from the server console with no client attached. Connected players are reported too,
 * because a marker is evaluated at its own position — stand on the marker for those numbers to
 * correspond.
 */
public class WeatherConditionProbeCommand extends AbstractWorldCommand {

    public WeatherConditionProbeCommand() {
        super("weatherprobe", "Dumps the inputs WeatherTriggerCondition reads");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- WeatherTriggerCondition probe ---\n");

        WeatherResource weather = store.getResource(WeatherResource.getResourceType());
        if (weather == null) {
            sendText(context, sb.append("WeatherResource: ABSENT\n").toString());
            return;
        }

        int forced = weather.getForcedWeatherIndex();
        sb.append("world: ").append(world.getName()).append("\n");
        sb.append("forcedWeatherIndex: ")
                .append(forced)
                .append(forced == 0 ? " (unforced)" : " (" + weatherName(forced) + " -- FORCED)")
                .append("\n");
        if (forced != 0) {
            sb.append("  !! while forced, WeatherSystem skips the environmentWeather refresh.\n");
            sb.append("  !! the map below is stale or empty; the condition cannot see the force.\n");
        }

        sb.append("objectiveMarkersEnabled: ")
                .append(world.getWorldConfig().isObjectiveMarkersEnabled())
                .append("\n");

        Int2IntMap map = weather.getEnvironmentWeather();
        sb.append("environmentWeather: ").append(map.size()).append(" entries\n");
        if (map.isEmpty()) {
            sb.append("  !! EMPTY -- every lookup returns Integer.MIN_VALUE, so EVERY\n");
            sb.append("  !! WeatherTriggerCondition is false regardless of which ids it names.\n");
        } else {
            for (Int2IntMap.Entry e : map.int2IntEntrySet()) {
                sb.append("  env ")
                        .append(e.getIntKey())
                        .append(" (")
                        .append(environmentName(e.getIntKey()))
                        .append(") -> weather ")
                        .append(e.getIntValue())
                        .append(" (")
                        .append(weatherName(e.getIntValue()))
                        .append(")\n");
            }
        }

        var playerRefs = world.getPlayerRefs();
        sb.append("players: ").append(playerRefs.size()).append("\n");
        for (PlayerRef playerRef : playerRefs) {
            appendPlayer(sb, world, store, weather, playerRef);
        }

        sendText(context, sb.toString());
    }

    private static void appendPlayer(
            StringBuilder sb, World world, Store<EntityStore> store, WeatherResource weather, PlayerRef playerRef) {
        sb.append("  [").append(playerRef.getUsername()).append("] ");

        Ref<EntityStore> ref = playerRef.getReference();
        TransformComponent transform =
                ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sb.append("TransformComponent ABSENT\n");
            return;
        }

        Ref<ChunkStore> sectionRef = transform.getSectionRef();
        if (sectionRef == null || !sectionRef.isValid()) {
            sb.append("sectionRef ")
                    .append(sectionRef == null ? "null" : "INVALID")
                    .append(" -- condition returns false here, before weather is consulted\n");
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        ChunkSection section = chunkStore.getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) {
            sb.append("ChunkSection ABSENT\n");
            return;
        }

        Ref<ChunkStore> columnRef = section.getChunkColumnReference();
        if (columnRef == null || !columnRef.isValid()) {
            sb.append("columnRef ")
                    .append(columnRef == null ? "null" : "INVALID")
                    .append(" -- condition returns false here, before weather is consulted\n");
            return;
        }

        BlockChunk chunk = chunkStore.getComponent(columnRef, BlockChunk.getComponentType());
        if (chunk == null) {
            sb.append("BlockChunk ABSENT\n");
            return;
        }

        int env = chunk.getEnvironment(transform.getPosition());
        int resolved = weather.getWeatherIndexForEnvironment(env);
        sb.append("env ").append(env).append(" (").append(environmentName(env)).append(") -> ");
        if (resolved == Integer.MIN_VALUE) {
            sb.append("MIN_VALUE -- environment absent from the map; no id list can match\n");
        } else {
            sb.append(weatherName(resolved))
                    .append(" (")
                    .append(resolved)
                    .append(") -- a marker here fires iff its WeatherIds contain this\n");
        }
    }

    private static String weatherName(int index) {
        try {
            Weather asset = Weather.getAssetMap().getAsset(index);
            return asset == null ? "?" : String.valueOf(asset.getId());
        } catch (Exception e) {
            return "?";
        }
    }

    private static String environmentName(int index) {
        try {
            Environment asset = Environment.getAssetMap().getAsset(index);
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

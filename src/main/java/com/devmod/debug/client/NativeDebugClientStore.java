package com.devmod.debug.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.devmod.DevMod;
import com.devmod.debug.BeesPayload;
import com.devmod.debug.BlockUpdatesPayload;
import com.devmod.debug.BrainsPayload;
import com.devmod.debug.EntityGoalsPayload;
import com.devmod.debug.POIPayload;
import com.devmod.debug.RaidsPayload;
import com.devmod.debug.StructuresPayload;

/**
 * Client-side snapshots of the structure/POI/raid/brain/goal/bee/block-update data pushed by
 * {@code NativeDebugSender}.
 * <p>
 * The renderer reads only from here, so it never touches the integrated server's state from
 * the render thread. Each snapshot carries the wall-clock time it arrived and is treated as
 * gone once {@link #STALE_AFTER_MS} has passed, so a disconnect, a dimension change or a
 * feature toggle stops the boxes within a few update intervals instead of freezing the last
 * frame on screen forever.
 * <p>
 * Each snapshot is an immutable list published as a whole through a volatile field, so a reader
 * sees either the previous list or the new one, never a half-filled one.
 * <p>
 * Block updates are the one exception to the snapshot shape: they arrive as a stream of events
 * rather than a full picture of the current state, so they accumulate instead of replacing.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class NativeDebugClientStore {

    /** Sender pushes every 5 ticks (~250 ms); 2 s leaves slack for a lag spike without sticking. */
    private static final long STALE_AFTER_MS = 2000;

    private static volatile List<StructuresPayload.StructureBox> structures = List.of();
    private static volatile long structuresTime;

    private static volatile List<POIPayload.POIInfo> pois = List.of();
    private static volatile long poisTime;

    private static volatile List<RaidsPayload.RaidInfo> raids = List.of();
    private static volatile long raidsTime;

    private static volatile List<BrainsPayload.TargetLink> brains = List.of();
    private static volatile long brainsTime;

    private static volatile List<EntityGoalsPayload.MobGoals> goals = List.of();
    private static volatile long goalsTime;

    private static volatile List<BeesPayload.BeeInfo> bees = List.of();
    private static volatile long beesTime;

    /**
     * How long one block-update marker stays visible. Short enough that a propagating update
     * reads as a wave rather than a solid blob, long enough to catch at 60 fps.
     */
    private static final long BLOCK_UPDATE_LINGER_MS = 1500;

    /**
     * Cap on retained markers. A saturated sender pushes {@code BlockUpdatesPayload.maxPositions()}
     * every interval, which over the linger window would be several thousand; keeping the newest
     * few hundred bounds both memory and the line batch without losing the leading edge.
     */
    private static final int MAX_BLOCK_UPDATE_MARKERS = 512;

    private static volatile List<BlockUpdateMarker> blockUpdates = List.of();

    /** One block that received a neighbour update, stamped with the client time it arrived. */
    public record BlockUpdateMarker(BlockPos pos, long receivedAtMs) {}

    private NativeDebugClientStore() {}

    public static void setStructures(StructuresPayload payload) {
        structures = List.copyOf(payload.boxes());
        structuresTime = System.currentTimeMillis();
    }

    public static void setPois(POIPayload payload) {
        pois = List.copyOf(payload.pois());
        poisTime = System.currentTimeMillis();
    }

    public static void setRaids(RaidsPayload payload) {
        raids = List.copyOf(payload.raids());
        raidsTime = System.currentTimeMillis();
    }

    public static void setBrains(BrainsPayload payload) {
        brains = List.copyOf(payload.targets());
        brainsTime = System.currentTimeMillis();
    }

    public static void setGoals(EntityGoalsPayload payload) {
        goals = List.copyOf(payload.mobs());
        goalsTime = System.currentTimeMillis();
    }

    public static void setBees(BeesPayload payload) {
        bees = List.copyOf(payload.bees());
        beesTime = System.currentTimeMillis();
    }

    /**
     * Append a batch of block updates, dropping markers that have outlived the fade and the
     * oldest survivors once the cap is hit. Builds a fresh immutable list so the volatile handoff
     * to the render thread stays the same as for the snapshots.
     */
    public static void addBlockUpdates(BlockUpdatesPayload payload) {
        long now = System.currentTimeMillis();
        List<BlockUpdateMarker> previous = blockUpdates;
        List<BlockUpdateMarker> merged = new ArrayList<>(previous.size() + payload.positions().size());

        for (BlockUpdateMarker marker : previous) {
            if (now - marker.receivedAtMs() <= BLOCK_UPDATE_LINGER_MS) {
                merged.add(marker);
            }
        }
        for (BlockPos pos : payload.positions()) {
            merged.add(new BlockUpdateMarker(pos, now));
        }

        int overflow = merged.size() - MAX_BLOCK_UPDATE_MARKERS;
        blockUpdates = List.copyOf(overflow > 0 ? merged.subList(overflow, merged.size()) : merged);
    }

    public static List<StructuresPayload.StructureBox> getStructures() {
        return fresh(structuresTime) ? structures : List.of();
    }

    public static List<POIPayload.POIInfo> getPois() {
        return fresh(poisTime) ? pois : List.of();
    }

    public static List<RaidsPayload.RaidInfo> getRaids() {
        return fresh(raidsTime) ? raids : List.of();
    }

    public static List<BrainsPayload.TargetLink> getBrains() {
        return fresh(brainsTime) ? brains : List.of();
    }

    public static List<EntityGoalsPayload.MobGoals> getGoals() {
        return fresh(goalsTime) ? goals : List.of();
    }

    public static List<BeesPayload.BeeInfo> getBees() {
        return fresh(beesTime) ? bees : List.of();
    }

    /**
     * Markers in arrival order, oldest first, or nothing once even the newest has faded out.
     * Individual markers may still be expired, so the renderer computes each one's age anyway.
     */
    public static List<BlockUpdateMarker> getBlockUpdates() {
        List<BlockUpdateMarker> markers = blockUpdates;
        if (markers.isEmpty()) {
            return markers;
        }
        long newest = markers.get(markers.size() - 1).receivedAtMs();
        return System.currentTimeMillis() - newest > BLOCK_UPDATE_LINGER_MS ? List.of() : markers;
    }

    /** Lifetime of a block-update marker, for the renderer's fade. */
    public static long blockUpdateLingerMs() {
        return BLOCK_UPDATE_LINGER_MS;
    }

    public static void clear() {
        structures = List.of();
        structuresTime = 0;
        pois = List.of();
        poisTime = 0;
        raids = List.of();
        raidsTime = 0;
        brains = List.of();
        brainsTime = 0;
        goals = List.of();
        goalsTime = 0;
        bees = List.of();
        beesTime = 0;
        blockUpdates = List.of();
    }

    private static boolean fresh(long stamp) {
        return stamp != 0 && System.currentTimeMillis() - stamp <= STALE_AFTER_MS;
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}

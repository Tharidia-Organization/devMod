package com.devmod.debug.client;

import java.util.List;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.devmod.DevMod;
import com.devmod.debug.POIPayload;
import com.devmod.debug.RaidsPayload;
import com.devmod.debug.StructuresPayload;

/**
 * Client-side snapshots of the structure/POI/raid data pushed by {@code NativeDebugSender}.
 * <p>
 * The renderer reads only from here, so it never touches the integrated server's state from
 * the render thread. Each snapshot carries the wall-clock time it arrived and is treated as
 * gone once {@link #STALE_AFTER_MS} has passed, so a disconnect, a dimension change or a
 * feature toggle stops the boxes within a few update intervals instead of freezing the last
 * frame on screen forever.
 * <p>
 * Each snapshot is an immutable list published as a whole through a volatile field, so a reader
 * sees either the previous list or the new one, never a half-filled one.
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

    public static List<StructuresPayload.StructureBox> getStructures() {
        return fresh(structuresTime) ? structures : List.of();
    }

    public static List<POIPayload.POIInfo> getPois() {
        return fresh(poisTime) ? pois : List.of();
    }

    public static List<RaidsPayload.RaidInfo> getRaids() {
        return fresh(raidsTime) ? raids : List.of();
    }

    public static void clear() {
        structures = List.of();
        structuresTime = 0;
        pois = List.of();
        poisTime = 0;
        raids = List.of();
        raidsTime = 0;
    }

    private static boolean fresh(long stamp) {
        return stamp != 0 && System.currentTimeMillis() - stamp <= STALE_AFTER_MS;
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}

package com.devmod.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Network payload to sync zone layout for debug rendering.
 * Sent from server to client when a player enters an arena with zones.
 */
public record ZoneDebugPayload(
    boolean enabled,
    int baseY,
    List<ZoneData> zones
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "zone_debug"));
    public static final Type<ZoneDebugPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, ZoneDebugPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull ZoneDebugPayload payload) {
                buf.writeBoolean(payload.enabled);
                buf.writeInt(payload.baseY);
                buf.writeInt(payload.zones.size());
                for (ZoneData zone : payload.zones) {
                    zone.write(buf);
                }
            }

            @Override
            public ZoneDebugPayload decode(@Nonnull FriendlyByteBuf buf) {
                boolean enabled = buf.readBoolean();
                int baseY = buf.readInt();
                int zoneCount = buf.readInt();
                List<ZoneData> zones = new ArrayList<>(zoneCount);
                for (int i = 0; i < zoneCount; i++) {
                    zones.add(ZoneData.read(buf));
                }
                return new ZoneDebugPayload(enabled, baseY, zones);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // enabled
        size += 4; // baseY
        size += 4; // zone count (int)
        for (ZoneData zone : zones) {
            size += estimateZoneSize(zone);
        }
        return size;
    }

    private static int estimateZoneSize(ZoneData zone) {
        int size = estimatedUtfSize(zone.name);
        size += 4; // shape
        size += 4; // x1
        size += 4; // z1
        size += 4; // x2
        size += 4; // z2
        size += 4; // radius
        size += 4; // innerRadius
        size += estimatedUtfSize(zone.biomeId);
        size += 4; // blockLight
        size += 4; // timeConfig
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Creates a payload to enable zone debug with the given layout.
     */
    public static ZoneDebugPayload enable(ZoneLayout layout, int baseY) {
        List<ZoneData> zones = new ArrayList<>();
        for (ArenaZone zone : layout.zones()) {
            zones.add(ZoneData.from(zone));
        }
        return new ZoneDebugPayload(true, baseY, zones);
    }

    /**
     * Creates a payload to disable zone debug rendering.
     */
    public static ZoneDebugPayload disable() {
        return new ZoneDebugPayload(false, 0, List.of());
    }

    /**
     * Simplified zone data for network serialization.
     */
    public record ZoneData(
        String name,
        int shape,  // 0=RECTANGULAR, 1=CIRCULAR, 2=RING
        int x1, int z1, int x2, int z2,
        int radius,
        int innerRadius,
        String biomeId,
        int blockLight,
        int timeConfig  // 0=ANY, 1=DAY, 2=NIGHT, 3=DUSK, 4=DAWN
    ) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(Objects.requireNonNull(name));
            buf.writeInt(shape);
            buf.writeInt(x1);
            buf.writeInt(z1);
            buf.writeInt(x2);
            buf.writeInt(z2);
            buf.writeInt(radius);
            buf.writeInt(innerRadius);
            buf.writeUtf(Objects.requireNonNull(biomeId));
            buf.writeInt(blockLight);
            buf.writeInt(timeConfig);
        }

        public static ZoneData read(FriendlyByteBuf buf) {
            return new ZoneData(
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt()
            );
        }

        public static ZoneData from(ArenaZone zone) {
            ArenaZone.ZoneBounds bounds = zone.bounds();
            ZoneEnvironment env = zone.environment();

            int shape = switch (zone.shape()) {
                case RECTANGULAR -> 0;
                case CIRCULAR -> 1;
                case RING -> 2;
            };

            String biomeId = env.biome()
                .map(ResourceLocation::toString)
                .orElse("");

            int timeConfig = switch (env.time()) {
                case ANY -> 0;
                case DAY -> 1;
                case NIGHT -> 2;
                case DUSK -> 3;
                case DAWN -> 4;
            };

            return new ZoneData(
                zone.name(),
                shape,
                bounds.x1(),
                bounds.z1(),
                bounds.x2(),
                bounds.z2(),
                bounds.radius().orElse(0),
                bounds.innerRadius().orElse(0),
                biomeId,
                env.lighting().blockLight(),
                timeConfig
            );
        }

        /**
         * Reconstructs an ArenaZone from this data.
         */
        public ArenaZone toArenaZone() {
            ArenaZone.ZoneShape zoneShape = switch (shape) {
                case 1 -> ArenaZone.ZoneShape.CIRCULAR;
                case 2 -> ArenaZone.ZoneShape.RING;
                default -> ArenaZone.ZoneShape.RECTANGULAR;
            };

            ArenaZone.ZoneBounds bounds = new ArenaZone.ZoneBounds(
                x1, z1, x2, z2,
                radius > 0 ? Optional.of(radius) : Optional.empty(),
                innerRadius > 0 ? Optional.of(innerRadius) : Optional.empty()
            );

            ZoneEnvironment.TimeConfig time = switch (timeConfig) {
                case 1 -> ZoneEnvironment.TimeConfig.DAY;
                case 2 -> ZoneEnvironment.TimeConfig.NIGHT;
                case 3 -> ZoneEnvironment.TimeConfig.DUSK;
                case 4 -> ZoneEnvironment.TimeConfig.DAWN;
                default -> ZoneEnvironment.TimeConfig.ANY;
            };

            Optional<ResourceLocation> biome = biomeId.isEmpty()
                ? Optional.empty()
                : Optional.of(ResourceLocation.parse(Objects.requireNonNull(biomeId)));

            ZoneEnvironment env = new ZoneEnvironment(
                biome,
                Optional.empty(),
                new ZoneEnvironment.LightingConfig(15, blockLight, false),
                time
            );

            return new ArenaZone(name, zoneShape, bounds, env, List.of(), 0);
        }
    }

    /**
     * Reconstructs a ZoneLayout from this payload.
     */
    @Nullable
    public ZoneLayout toZoneLayout() {
        if (!enabled || zones.isEmpty()) {
            return null;
        }

        List<ArenaZone> arenaZones = new ArrayList<>();
        for (ZoneData data : zones) {
            arenaZones.add(data.toArenaZone());
        }

        return new ZoneLayout(
            arenaZones,
            ZoneLayout.LayoutStrategy.CUSTOM,
            com.devmod.arena.zone.ZoneTransition.HARD,
            0,
            java.util.Map.of()
        );
    }
}

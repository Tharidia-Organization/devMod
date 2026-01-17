package com.devmod.template.data;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Definition of a room decoration component.
 * Components are reusable building blocks that can be placed in rooms.
 */
public record ComponentDefinition(
    @Nonnull String id,
    @Nonnull String displayName,
    @Nonnull ComponentCategory category,
    @Nonnull Vec3i baseSize,
    int minRoomSize,
    boolean requiresWall,
    @Nonnull List<String> compatibleZones
) {
    public static final Codec<ComponentDefinition> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(ComponentDefinition::id),
            Codec.STRING.fieldOf("displayName").forGetter(ComponentDefinition::displayName),
            Codec.STRING.fieldOf("category").xmap(ComponentCategory::fromString, ComponentCategory::getSerializedName)
                .forGetter(ComponentDefinition::category),
            Vec3i.CODEC.fieldOf("baseSize").forGetter(ComponentDefinition::baseSize),
            Codec.INT.fieldOf("minRoomSize").forGetter(ComponentDefinition::minRoomSize),
            Codec.BOOL.fieldOf("requiresWall").forGetter(ComponentDefinition::requiresWall),
            Codec.STRING.listOf().fieldOf("compatibleZones").forGetter(ComponentDefinition::compatibleZones)
        ).apply(instance, ComponentDefinition::new)
    );

    public static final StreamCodec<FriendlyByteBuf, ComponentDefinition> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ComponentDefinition decode(FriendlyByteBuf buf) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            ComponentCategory category = ComponentCategory.fromString(buf.readUtf());
            Vec3i baseSize = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            int minRoomSize = buf.readVarInt();
            boolean requiresWall = buf.readBoolean();
            int zoneCount = buf.readVarInt();
            List<String> zones = new java.util.ArrayList<>(zoneCount);
            for (int i = 0; i < zoneCount; i++) {
                zones.add(buf.readUtf());
            }
            return new ComponentDefinition(id, displayName, category, baseSize, minRoomSize, requiresWall, zones);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ComponentDefinition def) {
            buf.writeUtf(def.id);
            buf.writeUtf(def.displayName);
            buf.writeUtf(def.category.getSerializedName());
            buf.writeVarInt(def.baseSize.getX());
            buf.writeVarInt(def.baseSize.getY());
            buf.writeVarInt(def.baseSize.getZ());
            buf.writeVarInt(def.minRoomSize);
            buf.writeBoolean(def.requiresWall);
            buf.writeVarInt(def.compatibleZones.size());
            for (String zone : def.compatibleZones) {
                buf.writeUtf(zone);
            }
        }
    };

    public ComponentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(baseSize, "baseSize");
        Objects.requireNonNull(compatibleZones, "compatibleZones");
    }

    /**
     * Check if this component is compatible with a zone type.
     */
    public boolean isCompatibleWith(String zoneId) {
        return compatibleZones.isEmpty() || compatibleZones.contains(zoneId) || compatibleZones.contains("*");
    }

    /**
     * Check if this component fits in a room of given size.
     */
    public boolean fitsInRoom(int roomWidth, int roomDepth) {
        int roomSize = Math.min(roomWidth, roomDepth);
        return roomSize >= minRoomSize;
    }
}

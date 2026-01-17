package com.devmod.template.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A room decoration template.
 * Templates define which components are placed in a room and how.
 */
public record RoomTemplate(
    @Nonnull UUID id,
    @Nonnull String templateId,
    @Nonnull String displayName,
    @Nonnull String zoneId,
    @Nonnull List<ComponentPlacement> components,
    @Nonnull Map<String, String> materialOverrides,
    int minWidth,
    int minDepth,
    boolean isCustom,
    long createdAt,
    int revision
) {
    public static final Codec<RoomTemplate> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(RoomTemplate::id),
            Codec.STRING.fieldOf("templateId").forGetter(RoomTemplate::templateId),
            Codec.STRING.fieldOf("displayName").forGetter(RoomTemplate::displayName),
            Codec.STRING.fieldOf("zoneId").forGetter(RoomTemplate::zoneId),
            ComponentPlacement.CODEC.listOf().fieldOf("components").forGetter(RoomTemplate::components),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("materialOverrides")
                .forGetter(RoomTemplate::materialOverrides),
            Codec.INT.fieldOf("minWidth").forGetter(RoomTemplate::minWidth),
            Codec.INT.fieldOf("minDepth").forGetter(RoomTemplate::minDepth),
            Codec.BOOL.fieldOf("isCustom").forGetter(RoomTemplate::isCustom),
            Codec.LONG.fieldOf("createdAt").forGetter(RoomTemplate::createdAt),
            Codec.INT.fieldOf("revision").forGetter(RoomTemplate::revision)
        ).apply(instance, (id, templateId, displayName, zoneId, components, materialOverrides,
                           minWidth, minDepth, isCustom, createdAt, revision) ->
            new RoomTemplate(id, templateId, displayName, zoneId, components, materialOverrides,
                Objects.requireNonNull(minWidth, "minWidth").intValue(),
                Objects.requireNonNull(minDepth, "minDepth").intValue(),
                Objects.requireNonNull(isCustom, "isCustom").booleanValue(),
                Objects.requireNonNull(createdAt, "createdAt").longValue(),
                Objects.requireNonNull(revision, "revision").intValue()))
    );

    public static final StreamCodec<FriendlyByteBuf, RoomTemplate> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RoomTemplate decode(@Nonnull FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String templateId = buf.readUtf();
            String displayName = buf.readUtf();
            String zoneId = buf.readUtf();

            int componentCount = buf.readVarInt();
            List<ComponentPlacement> components = new ArrayList<>(componentCount);
            for (int i = 0; i < componentCount; i++) {
                components.add(ComponentPlacement.STREAM_CODEC.decode(buf));
            }

            int overrideCount = buf.readVarInt();
            Map<String, String> materialOverrides = new HashMap<>(overrideCount);
            for (int i = 0; i < overrideCount; i++) {
                materialOverrides.put(buf.readUtf(), buf.readUtf());
            }

            int minWidth = buf.readVarInt();
            int minDepth = buf.readVarInt();
            boolean isCustom = buf.readBoolean();
            long createdAt = buf.readLong();
            int revision = buf.readVarInt();

            return new RoomTemplate(id, templateId, displayName, zoneId, components,
                materialOverrides, minWidth, minDepth, isCustom, createdAt, revision);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull RoomTemplate template) {
            buf.writeUUID(template.id);
            buf.writeUtf(template.templateId);
            buf.writeUtf(template.displayName);
            buf.writeUtf(template.zoneId);

            buf.writeVarInt(template.components.size());
            for (ComponentPlacement comp : template.components) {
                ComponentPlacement.STREAM_CODEC.encode(buf, Objects.requireNonNull(comp));
            }

            buf.writeVarInt(template.materialOverrides.size());
            for (Map.Entry<String, String> entry : template.materialOverrides.entrySet()) {
                buf.writeUtf(Objects.requireNonNull(entry.getKey()));
                buf.writeUtf(Objects.requireNonNull(entry.getValue()));
            }

            buf.writeVarInt(template.minWidth);
            buf.writeVarInt(template.minDepth);
            buf.writeBoolean(template.isCustom);
            buf.writeLong(template.createdAt);
            buf.writeVarInt(template.revision);
        }
    };

    public RoomTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(materialOverrides, "materialOverrides");
    }

    /**
     * Check if this template fits the given room dimensions.
     */
    public boolean fitsRoom(int width, int depth) {
        return width >= minWidth && depth >= minDepth;
    }

    /**
     * Get a material override or null if not set.
     */
    @Nullable
    public String getMaterialOverride(String key) {
        return materialOverrides.get(key);
    }

    /**
     * Create a copy with incremented revision.
     */
    public RoomTemplate withNewRevision() {
        return new RoomTemplate(id, templateId, displayName, zoneId, components,
            materialOverrides, minWidth, minDepth, isCustom, createdAt, revision + 1);
    }

    /**
     * Create a copy with updated components.
     */
    public RoomTemplate withComponents(List<ComponentPlacement> newComponents) {
        return new RoomTemplate(id, templateId, displayName, zoneId, newComponents,
            materialOverrides, minWidth, minDepth, isCustom, createdAt, revision + 1);
    }

    /**
     * Builder for creating templates.
     */
    public static Builder builder(String templateId, String zoneId) {
        return new Builder(templateId, zoneId);
    }

    public static class Builder {
        private final String templateId;
        private final String zoneId;
        private String displayName;
        private List<ComponentPlacement> components = new ArrayList<>();
        private Map<String, String> materialOverrides = new HashMap<>();
        private int minWidth = 16;
        private int minDepth = 16;
        private boolean isCustom = false;

        private Builder(String templateId, String zoneId) {
            this.templateId = templateId;
            this.zoneId = zoneId;
            this.displayName = templateId;
        }

        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder component(ComponentPlacement placement) {
            this.components.add(placement);
            return this;
        }

        public Builder component(String componentId, RelativePosition position) {
            this.components.add(ComponentPlacement.of(componentId, position));
            return this;
        }

        public Builder material(String key, String value) {
            this.materialOverrides.put(key, value);
            return this;
        }

        public Builder minSize(int width, int depth) {
            this.minWidth = width;
            this.minDepth = depth;
            return this;
        }

        public Builder custom(boolean isCustom) {
            this.isCustom = isCustom;
            return this;
        }

        @Nonnull
        public RoomTemplate build() {
            return new RoomTemplate(
                Objects.requireNonNull(UUID.randomUUID()),
                templateId,
                displayName,
                zoneId,
                Objects.requireNonNull(List.copyOf(components)),
                Objects.requireNonNull(Map.copyOf(materialOverrides)),
                minWidth,
                minDepth,
                isCustom,
                System.currentTimeMillis(),
                1
            );
        }
    }
}

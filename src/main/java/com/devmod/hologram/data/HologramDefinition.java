package com.devmod.hologram.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Complete definition of a hologram, including content, style, position, and metadata.
 * This is the primary data structure for the Hologram Builder system.
 */
public record HologramDefinition(
    UUID id,
    String label,
    HologramType type,
    List<String> lines,
    HologramStyle style,
    HologramPosition position,
    HologramOptions options,
    @Nullable UUID creatorUUID,
    long createdAt,
    long updatedAt,
    int revision
) {
    /**
     * Maximum number of lines per hologram.
     */
    public static final int MAX_LINES = 16;

    /**
     * Maximum characters per line.
     */
    public static final int MAX_LINE_LENGTH = 128;

    public static final Codec<HologramDefinition> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(HologramDefinition::id),
            Codec.STRING.fieldOf("label").forGetter(HologramDefinition::label),
            HologramType.CODEC.fieldOf("type").forGetter(HologramDefinition::type),
            Codec.STRING.listOf().fieldOf("lines").forGetter(HologramDefinition::lines),
            HologramStyle.CODEC.fieldOf("style").forGetter(HologramDefinition::style),
            HologramPosition.CODEC.fieldOf("position").forGetter(HologramDefinition::position),
            HologramOptions.CODEC.fieldOf("options").forGetter(HologramDefinition::options),
            UUIDUtil.CODEC.optionalFieldOf("creator").forGetter(d -> Optional.ofNullable(d.creatorUUID())),
            Codec.LONG.fieldOf("createdAt").forGetter(HologramDefinition::createdAt),
            Codec.LONG.fieldOf("updatedAt").forGetter(HologramDefinition::updatedAt),
            Codec.INT.fieldOf("revision").forGetter(HologramDefinition::revision)
        ).apply(instance, (id, label, type, lines, style, pos, opts, creatorOpt, created, updated, rev) ->
            new HologramDefinition(id, label, type, lines, style, pos, opts,
                creatorOpt.orElse(null), created, updated, rev))
    );

    public static final StreamCodec<FriendlyByteBuf, HologramDefinition> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramDefinition decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String label = buf.readUtf();
            HologramType type = buf.readEnum(HologramType.class);
            int lineCount = buf.readVarInt();
            List<String> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                lines.add(buf.readUtf());
            }
            HologramStyle style = HologramStyle.STREAM_CODEC.decode(buf);
            HologramPosition position = HologramPosition.STREAM_CODEC.decode(buf);
            HologramOptions options = HologramOptions.STREAM_CODEC.decode(buf);
            UUID creatorUUID = buf.readBoolean() ? buf.readUUID() : null;
            long createdAt = buf.readLong();
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            return new HologramDefinition(id, label, type, lines, style, position, options,
                creatorUUID, createdAt, updatedAt, revision);
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramDefinition def) {
            buf.writeUUID(def.id);
            buf.writeUtf(def.label);
            buf.writeEnum(def.type);
            buf.writeVarInt(def.lines.size());
            for (String line : def.lines) {
                buf.writeUtf(line);
            }
            HologramStyle.STREAM_CODEC.encode(buf, def.style);
            HologramPosition.STREAM_CODEC.encode(buf, def.position);
            HologramOptions.STREAM_CODEC.encode(buf, def.options);
            buf.writeBoolean(def.creatorUUID != null);
            if (def.creatorUUID != null) {
                buf.writeUUID(def.creatorUUID);
            }
            buf.writeLong(def.createdAt);
            buf.writeLong(def.updatedAt);
            buf.writeVarInt(def.revision);
        }
    };

    public HologramDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(options, "options");

        // Enforce line limits
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
        }
    }

    /**
     * Creates a new builder for HologramDefinition.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this definition's values.
     */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .label(label)
            .type(type)
            .lines(new ArrayList<>(lines))
            .style(style)
            .position(position)
            .options(options)
            .creator(creatorUUID)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .revision(revision);
    }

    /**
     * Returns a copy with incremented revision and updated timestamp.
     */
    public HologramDefinition withUpdate(List<String> newLines, HologramStyle newStyle, HologramOptions newOptions) {
        return new HologramDefinition(
            id, label, type, newLines, newStyle, position, newOptions,
            creatorUUID, createdAt, System.currentTimeMillis(), revision + 1
        );
    }

    /**
     * Returns a copy with a new position.
     */
    public HologramDefinition withPosition(HologramPosition newPosition) {
        return new HologramDefinition(
            id, label, type, lines, style, newPosition, options,
            creatorUUID, createdAt, System.currentTimeMillis(), revision + 1
        );
    }

    /**
     * Builder for HologramDefinition.
     */
    public static class Builder {
        @Nullable private UUID id;
        private String label = "New Hologram";
        private HologramType type = HologramType.STATIC;
        private List<String> lines = new ArrayList<>();
        private HologramStyle style = HologramStyle.DEFAULT;
        private HologramPosition position = HologramPosition.ZERO;
        private HologramOptions options = HologramOptions.DEFAULT;
        @Nullable private UUID creatorUUID;
        private long createdAt;
        private long updatedAt;
        private int revision = 1;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder type(HologramType type) { this.type = type; return this; }
        public Builder lines(List<String> lines) { this.lines = lines; return this; }
        public Builder addLine(String line) { this.lines.add(line); return this; }
        public Builder style(HologramStyle style) { this.style = style; return this; }
        public Builder position(HologramPosition position) { this.position = position; return this; }
        public Builder options(HologramOptions options) { this.options = options; return this; }
        public Builder creator(@Nullable UUID uuid) { this.creatorUUID = uuid; return this; }
        public Builder createdAt(long time) { this.createdAt = time; return this; }
        public Builder updatedAt(long time) { this.updatedAt = time; return this; }
        public Builder revision(int rev) { this.revision = rev; return this; }

        /**
         * Sets position using block coordinates and dimension.
         */
        public Builder position(BlockPos blockPos, ResourceLocation dimension) {
            this.position = HologramPosition.aboveBlock(blockPos, dimension);
            return this;
        }

        /**
         * Initializes from a preset.
         */
        public Builder fromPreset(HologramPreset preset, BlockPos hubOrigin, ResourceLocation dimension) {
            this.label = preset.getLabel();
            this.type = preset.getType();
            this.lines = new ArrayList<>(preset.getDefaultLines());
            BlockPos presetPos = preset.getDefaultOffset().applyTo(hubOrigin);
            this.position = HologramPosition.atBlock(presetPos, dimension);
            return this;
        }

        public HologramDefinition build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            long now = System.currentTimeMillis();
            if (createdAt == 0) {
                createdAt = now;
            }
            if (updatedAt == 0) {
                updatedAt = now;
            }
            return new HologramDefinition(id, label, type, lines, style, position, options,
                creatorUUID, createdAt, updatedAt, revision);
        }
    }
}

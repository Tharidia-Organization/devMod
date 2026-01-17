package com.devmod.area.template;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Categories for organizing area templates.
 */
public enum TemplateCategory implements StringRepresentable {
    /**
     * Combat arenas and PvP areas.
     */
    ARENA("arena"),

    /**
     * Central hub areas for player gathering.
     */
    HUB("hub"),

    /**
     * Dungeon-style areas with challenges.
     */
    DUNGEON("dungeon"),

    /**
     * Player housing and living spaces.
     */
    HOUSING("housing"),

    /**
     * Natural landscape and terrain areas.
     */
    LANDSCAPE("landscape"),

    /**
     * Utility/functional areas (spawns, teleporters, etc.).
     */
    UTILITY("utility"),

    /**
     * Uncategorized templates.
     */
    OTHER("other");

    public static final Codec<TemplateCategory> CODEC = StringRepresentable.fromEnum(TemplateCategory::values);

    /**
     * M-10 fix: Safe decoder that returns OTHER for invalid ordinals instead of silent clamping.
     */
    public static final StreamCodec<ByteBuf, TemplateCategory> STREAM_CODEC = StreamCodec.of(
        (buf, category) -> ByteBufCodecs.VAR_INT.encode(buf, category.ordinal()),
        buf -> {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            TemplateCategory[] vals = values();
            if (ordinal < 0 || ordinal >= vals.length) {
                org.slf4j.LoggerFactory.getLogger(TemplateCategory.class)
                    .warn("[TemplateCategory] Unknown ordinal {} (max: {}), defaulting to OTHER.",
                        ordinal, vals.length - 1);
                return OTHER;
            }
            return vals[ordinal];
        }
    );

    private final String name;

    TemplateCategory(String name) {
        this.name = name;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Gets a category by its serialized name.
     *
     * @param name The serialized name
     * @return The category, or OTHER if not found
     */
    @Nonnull
    public static TemplateCategory byName(@Nonnull String name) {
        for (TemplateCategory cat : values()) {
            if (cat.name.equalsIgnoreCase(name)) {
                return cat;
            }
        }
        return OTHER;
    }
}

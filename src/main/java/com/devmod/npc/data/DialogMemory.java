package com.devmod.npc.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Tracks dialog history and custom data for a player-NPC interaction.
 * Used for persistent dialog state across sessions.
 *
 * <p>Features:
 * <ul>
 *   <li>Tracks all selected option IDs</li>
 *   <li>Stores custom key-value data set by RememberChoice action</li>
 *   <li>Records last interaction timestamp</li>
 * </ul>
 */
public record DialogMemory(
    @Nonnull UUID playerId,
    @Nonnull UUID npcId,
    @Nonnull List<String> selectedOptionIds,
    @Nonnull Map<String, String> customData,
    long lastInteraction
) {
    public static final Codec<DialogMemory> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("playerId").forGetter(DialogMemory::playerId),
            UUIDUtil.CODEC.fieldOf("npcId").forGetter(DialogMemory::npcId),
            Codec.STRING.listOf().fieldOf("selectedOptionIds").forGetter(DialogMemory::selectedOptionIds),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("customData").forGetter(DialogMemory::customData),
            Codec.LONG.fieldOf("lastInteraction").forGetter(DialogMemory::lastInteraction)
        ).apply(instance, DialogMemory::new)
    );

    public DialogMemory {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(selectedOptionIds, "selectedOptionIds");
        Objects.requireNonNull(customData, "customData");
    }

    /**
     * Creates a new empty memory for a player-NPC pair.
     */
    @Nonnull
    public static DialogMemory create(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        return new DialogMemory(
            playerId,
            npcId,
            new ArrayList<>(),
            new HashMap<>(),
            System.currentTimeMillis()
        );
    }

    /**
     * Returns a new memory with the option added to history.
     */
    @Nonnull
    public DialogMemory withSelectedOption(@Nonnull String optionId) {
        List<String> newOptions = new ArrayList<>(selectedOptionIds);
        if (!newOptions.contains(optionId)) {
            newOptions.add(optionId);
        }
        return new DialogMemory(playerId, npcId, newOptions, customData, System.currentTimeMillis());
    }

    /**
     * Returns a new memory with custom data added.
     */
    @Nonnull
    public DialogMemory withCustomData(@Nonnull String key, @Nonnull String value) {
        Map<String, String> newData = new HashMap<>(customData);
        newData.put(key, value);
        return new DialogMemory(playerId, npcId, selectedOptionIds, newData, System.currentTimeMillis());
    }

    /**
     * Returns a new memory with custom data removed.
     */
    @Nonnull
    public DialogMemory withoutCustomData(@Nonnull String key) {
        Map<String, String> newData = new HashMap<>(customData);
        newData.remove(key);
        return new DialogMemory(playerId, npcId, selectedOptionIds, newData, System.currentTimeMillis());
    }

    /**
     * Returns a new memory with updated timestamp.
     */
    @Nonnull
    public DialogMemory touch() {
        return new DialogMemory(playerId, npcId, selectedOptionIds, customData, System.currentTimeMillis());
    }

    /**
     * Checks if an option has been selected before.
     */
    public boolean hasSelectedOption(@Nonnull String optionId) {
        return selectedOptionIds.contains(optionId);
    }

    /**
     * Gets a custom data value.
     */
    @Nonnull
    public String getCustomData(@Nonnull String key, @Nonnull String defaultValue) {
        return customData.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if custom data exists.
     */
    public boolean hasCustomData(@Nonnull String key) {
        return customData.containsKey(key);
    }

    /**
     * Gets the number of options selected.
     */
    public int getSelectionCount() {
        return selectedOptionIds.size();
    }
}

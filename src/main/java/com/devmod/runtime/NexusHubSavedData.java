package com.devmod.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * Persists Nexus hub build state per world.
 */
public class NexusHubSavedData extends SavedData {
    public static final int CURRENT_VERSION = 14;
    private static final String DATA_NAME = "devmod_nexus_hub";

    private boolean built;
    private int version;
    private boolean locked;

    public NexusHubSavedData() {}

    public static NexusHubSavedData load(
        @Nonnull CompoundTag tag,
        @Nonnull HolderLookup.Provider registries
    ) {
        NexusHubSavedData data = new NexusHubSavedData();
        data.built = tag.getBoolean("built");
        data.version = tag.getInt("version");
        data.locked = tag.getBoolean("locked");
        return data;
    }

    @Override
    public CompoundTag save(
        @Nonnull CompoundTag tag,
        @Nonnull HolderLookup.Provider registries
    ) {
        tag.putBoolean("built", built);
        tag.putInt("version", version);
        tag.putBoolean("locked", locked);
        return tag;
    }

    public boolean isBuilt() {
        return built;
    }

    public int getVersion() {
        return version;
    }

    public boolean isLocked() {
        return locked;
    }

    public void markBuilt(int version) {
        this.built = true;
        this.version = version;
        setDirty();
    }

    public void markUnbuilt() {
        this.built = false;
        this.version = 0;
        setDirty();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setDirty();
    }

    public static NexusHubSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(NexusHubSavedData::new, NexusHubSavedData::load),
                DATA_NAME
            );
    }
}

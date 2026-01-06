package com.devmod.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Stores Nexus return locations per player.
 */
public final class NexusReturnSavedData extends SavedData {
    private static final String DATA_NAME = "devmod_nexus_return";
    private static final String TAG_RETURNS = "returns";

    private final Map<UUID, ReturnPoint> returns = new HashMap<>();

    public record ReturnPoint(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch) {}

    public static NexusReturnSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NexusReturnSavedData data = new NexusReturnSavedData();
        ListTag list = tag.getList(TAG_RETURNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("id")) {
                continue;
            }
            UUID id = entry.getUUID("id");
            String dimId = entry.getString("dim");
            ResourceLocation dimLoc = ResourceLocation.tryParse(dimId);
            if (dimLoc == null) {
                continue;
            }
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            int x = entry.getInt("x");
            int y = entry.getInt("y");
            int z = entry.getInt("z");
            float yaw = entry.getFloat("yaw");
            float pitch = entry.getFloat("pitch");
            data.returns.put(id, new ReturnPoint(dimKey, new BlockPos(x, y, z), yaw, pitch));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> entry : returns.entrySet()) {
            CompoundTag data = new CompoundTag();
            data.putUUID("id", entry.getKey());
            ReturnPoint point = entry.getValue();
            data.putString("dim", point.dimension().location().toString());
            data.putInt("x", point.pos().getX());
            data.putInt("y", point.pos().getY());
            data.putInt("z", point.pos().getZ());
            data.putFloat("yaw", point.yaw());
            data.putFloat("pitch", point.pitch());
            list.add(data);
        }
        tag.put(TAG_RETURNS, list);
        return tag;
    }

    public void recordReturn(ServerPlayer player) {
        if (player == null) {
            return;
        }
        returns.put(player.getUUID(), new ReturnPoint(
            player.level().dimension(),
            player.blockPosition(),
            player.getYRot(),
            player.getXRot()
        ));
        setDirty();
    }

    @Nullable
    public ReturnPoint getReturn(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return returns.get(playerId);
    }

    public void clearReturn(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (returns.remove(playerId) != null) {
            setDirty();
        }
    }

    public static NexusReturnSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(new Factory<>(NexusReturnSavedData::new, NexusReturnSavedData::load), DATA_NAME);
    }

    @Nullable
    public static ServerLevel resolveReturnLevel(MinecraftServer server, ReturnPoint point) {
        if (server == null || point == null) {
            return null;
        }
        return server.getLevel(Objects.requireNonNull(point.dimension(), "dimension"));
    }
}

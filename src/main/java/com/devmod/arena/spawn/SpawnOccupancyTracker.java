package com.devmod.arena.spawn;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
public class SpawnOccupancyTracker {

    private final Set<Long> occupied = ConcurrentHashMap.newKeySet();

    public boolean isOccupied(BlockPos pos) {
        return occupied.contains(pos.asLong());
    }

    public boolean markOccupied(BlockPos pos) {
        return occupied.add(pos.asLong());
    }

    public void clear() {
        occupied.clear();
    }
}

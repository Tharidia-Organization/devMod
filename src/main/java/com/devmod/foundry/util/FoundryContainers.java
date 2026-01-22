package com.devmod.foundry.util;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helpers for creating containers that mark their owner dirty on changes.
 */
public final class FoundryContainers {
    private FoundryContainers() {}

    public static SimpleContainer tracked(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        return new TrackedContainer(size);
    }

    public static SimpleContainer tracked(BlockEntity owner, int size) {
        SimpleContainer container = tracked(size);
        bind(container, owner);
        return container;
    }

    public static void bind(SimpleContainer container, BlockEntity owner) {
        Objects.requireNonNull(owner, "owner");
        if (container instanceof TrackedContainer tracked) {
            tracked.bind(owner);
        }
    }

    private static final class TrackedContainer extends SimpleContainer {
        @Nullable private BlockEntity owner;

        private TrackedContainer(int size) {
            super(size);
        }

        private void bind(BlockEntity owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntity currentOwner = owner;
            if (currentOwner != null) {
                currentOwner.setChanged();
            }
        }
    }
}

package com.devmod.client.collision.transform;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;

import com.devmod.collision.transform.AnimationSnapshot;
import com.devmod.collision.transform.TransformProvider;

public final class ClientTransformProvider implements TransformProvider {

    public static final ClientTransformProvider INSTANCE = new ClientTransformProvider();

    private ClientTransformProvider() {} // Singleton

    @Override
    @Nonnull
    public AnimationSnapshot extractTransforms(@Nonnull LivingEntity entity,
                                                float partialTick,
                                                long currentTick) {
        return ModelPartTransformExtractor.extractTransforms(
            Objects.requireNonNull(entity), partialTick, currentTick);
    }

    @Override
    public void clearCache(int entityId) {
        ModelPartTransformExtractor.clearCache(entityId);
    }

    @Override
    public void clearAllCaches() {
        ModelPartTransformExtractor.clearAllCaches();
    }

    @Override
    public int getCacheSize() {
        return ModelPartTransformExtractor.getCacheSize();
    }
}

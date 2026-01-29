package com.devmod.client.vfx.effekseer.api;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

public class ParticleEmitter {
    public enum Type {
        WORLD,
        FIRST_PERSON_MAINHAND,
        FIRST_PERSON_OFFHAND
    }

    public final int handle;
    public final Type type;

    protected final @Nullable EffekseerManager manager;
    private @Nullable PreDrawCallback callback;
    public boolean isVisible = true;
    public boolean isPaused = false;
    private boolean valid = true;

    public static ParticleEmitter dummy(Type type) {
        if (DUMMIES.isEmpty()) {
            for (Type entry : Type.values()) {
                DUMMIES.put(entry, new DummyParticleEmitter(entry));
            }
        }
        return Objects.requireNonNull(DUMMIES.get(type), "Type not in DUMMIES: " + type);
    }

    private static final EnumMap<Type, ParticleEmitter> DUMMIES = new EnumMap<>(Type.class);

    protected ParticleEmitter(int handle, @Nullable EffekseerManager manager, Type type) {
        this.handle = handle;
        this.manager = manager;
        this.type = type;
        setVisibility(true);
        resume();
    }

    public void pause() {
        if (valid && manager != null) {
            manager.getImpl().SetPaused(handle, true);
        }
        isPaused = true;
    }

    public void resume() {
        if (valid && manager != null) {
            manager.getImpl().SetPaused(handle, false);
        }
        isPaused = false;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisibility(boolean visible) {
        if (valid && manager != null) {
            manager.getImpl().SetShown(handle, visible);
        }
        isVisible = visible;
    }

    public void stop() {
        if (valid && manager != null) {
            valid = false;
            manager.getImpl().Stop(handle);
        }
    }

    public void setProgress(float frame) {
        if (valid && manager != null) {
            manager.getImpl().UpdateHandleToMoveToFrame(handle, frame);
        }
    }

    public void setPosition(float x, float y, float z) {
        if (valid && manager != null) {
            manager.getImpl().SetEffectPosition(handle, x, y, z);
        }
    }

    public void setRotation(float x, float y, float z) {
        if (valid && manager != null) {
            manager.getImpl().SetEffectRotation(handle, x, y, z);
        }
    }

    public void setScale(float x, float y, float z) {
        if (valid && manager != null) {
            manager.getImpl().SetEffectScale(handle, x, y, z);
        }
    }

    public void setTransformMatrix(float[] matrix) {
        if (valid && manager != null) {
            manager.getImpl().SetEffectTransformMatrix(
                handle,
                matrix[0], matrix[1], matrix[2], matrix[3],
                matrix[4], matrix[5], matrix[6], matrix[7],
                matrix[8], matrix[9], matrix[10], matrix[11]
            );
        }
    }

    public void setTransformMatrix(float[][] matrix) {
        if (valid && manager != null) {
            int i = 0;
            manager.getImpl().SetEffectTransformMatrix(
                handle,
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i++][3],
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i++][3],
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i][3]
            );
        }
    }

    public void setBaseTransformMatrix(float[] matrix) {
        if (valid && manager != null) {
            manager.getImpl().SetEffectTransformBaseMatrix(
                handle,
                matrix[0], matrix[1], matrix[2], matrix[3],
                matrix[4], matrix[5], matrix[6], matrix[7],
                matrix[8], matrix[9], matrix[10], matrix[11]
            );
        }
    }

    public void setBaseTransformMatrix(float[][] matrix) {
        if (valid && manager != null) {
            int i = 0;
            manager.getImpl().SetEffectTransformBaseMatrix(
                handle,
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i++][3],
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i++][3],
                matrix[i][0], matrix[i][1], matrix[i][2], matrix[i][3]
            );
        }
    }

    public boolean exists() {
        return valid && manager != null && manager.getImpl().Exists(handle);
    }

    public void setDynamicInput(int index, float value) {
        if (valid && manager != null) {
            manager.getImpl().SetDynamicInput(handle, index, value);
        }
    }

    public float getDynamicInput(int index) {
        if (valid && manager != null) {
            return manager.getImpl().GetDynamicInput(handle, index);
        }
        return 0;
    }

    public void sendTrigger(int index) {
        if (valid && manager != null) {
            manager.getImpl().SendTrigger(handle, index);
        }
    }

    public interface PreDrawCallback {
        void accept(ParticleEmitter emitter, float partialTicks);

        default PreDrawCallback andThen(PreDrawCallback after) {
            Objects.requireNonNull(after);
            return (emitter, partial) -> {
                accept(emitter, partial);
                after.accept(emitter, partial);
            };
        }
    }

    public void addPreDrawCallback(PreDrawCallback callback) {
        Optional.ofNullable(this.callback).ifPresentOrElse(
            existing -> this.callback = existing.andThen(callback),
            () -> this.callback = callback
        );
    }

    public void runPreDrawCallbacks(float partial) {
        Optional.ofNullable(callback).ifPresent(c -> c.accept(this, partial));
    }

    private static final class DummyParticleEmitter extends ParticleEmitter {
        private DummyParticleEmitter(Type type) {
            super(-1, null, type);
        }

        @Override
        public void pause() {
            isPaused = true;
        }

        @Override
        public void resume() {
            isPaused = false;
        }

        @Override
        public void setVisibility(boolean visible) {
            isVisible = visible;
        }

        @Override
        public void stop() {
        }

        @Override
        public void setProgress(float frame) {
        }

        @Override
        public void setPosition(float x, float y, float z) {
        }

        @Override
        public void setRotation(float x, float y, float z) {
        }

        @Override
        public void setScale(float x, float y, float z) {
        }

        @Override
        public void setTransformMatrix(float[] matrix) {
        }

        @Override
        public void setTransformMatrix(float[][] matrix) {
        }

        @Override
        public void setBaseTransformMatrix(float[] matrix) {
        }

        @Override
        public void setBaseTransformMatrix(float[][] matrix) {
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public void setDynamicInput(int index, float value) {
        }

        @Override
        public float getDynamicInput(int index) {
            return 0;
        }

        @Override
        public void sendTrigger(int index) {
        }
    }
}

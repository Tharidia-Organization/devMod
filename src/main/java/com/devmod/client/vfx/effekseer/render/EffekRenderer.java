package com.devmod.client.vfx.effekseer.render;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

import com.devmod.client.vfx.effekseer.api.DeviceType;
import com.devmod.client.vfx.effekseer.api.Effekseer;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.client.vfx.effekseer.installer.NativePlatform;
import com.devmod.client.vfx.effekseer.loader.EffekAssetLoader;

import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glDepthMask;

public class EffekRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffekRenderer.class);
    private static final FloatBuffer CAMERA_TRANSFORM_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final AtomicBoolean INIT = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_RENDER = new AtomicBoolean();

    public static void init() {
        if (INIT.compareAndSet(false, true)) {
            if (NativePlatform.isRunningOnUnsupportedPlatform()) {
                return;
            }
            if (Effekseer.getDeviceType() != DeviceType.OPENGL) {
                if (!Effekseer.init()) {
                    throw new ExceptionInInitializerError("Failed to initialize Effekseer");
                }
                Runtime.getRuntime().addShutdownHook(new Thread(Effekseer::terminate, "EffekseerShutdown"));
            }
        }
    }

    public static void renderWorldEffeks(float partialTick, Matrix4f viewMatrix, Matrix4f projection, Camera camera) {
        if (NativePlatform.isRunningOnUnsupportedPlatform()) {
            return;
        }
        RenderStateCapture.ensureSizedToMain();
        if (LOGGED_RENDER.compareAndSet(false, true)) {
            LOGGER.info("[Effekseer] renderWorldEffeks active");
        }
        draw(ParticleEmitter.Type.WORLD, partialTick, viewMatrix, projection, camera);
    }

    private static void draw(ParticleEmitter.Type type, float partialTick, Matrix4f viewMatrix,
            Matrix4f projection, Camera camera) {
        EffekAssetLoader loader = EffekAssetLoader.get();
        if (loader == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();

        projection.get(PROJECTION_BUFFER);
        transposeMatrix(PROJECTION_BUFFER);
        PROJECTION_BUFFER.get(PROJECTION_MATRIX_DATA);

        // Build camera matrix like AAAParticles does:
        // Start with identity, then translate by -camera position for WORLD particles
        Matrix4f cameraMatrix = new Matrix4f(viewMatrix);
        if (type == ParticleEmitter.Type.WORLD) {
            var camPos = camera.getPosition();
            cameraMatrix.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        }
        cameraMatrix.get(CAMERA_TRANSFORM_BUFFER);
        transposeMatrix(CAMERA_TRANSFORM_BUFFER);
        CAMERA_TRANSFORM_BUFFER.get(CAMERA_TRANSFORM_DATA);

        float deltaFrames = 60 * getDeltaTime(type);
        float realDelta = minecraft.isPaused() ? 0 : deltaFrames;

        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);

        RenderType.PARTICLES_TARGET.setupRenderState();
        RenderUtil.runPixelStoreCodeSafely(() -> {
            var background = RenderUtil.prepareBackgroundBuffer().orElse(null);
            loader.forEach((id, inst) -> inst.draw(
                type,
                camera.getLookVector(), camera.getPosition().toVector3f(),
                w, h,
                CAMERA_TRANSFORM_DATA, PROJECTION_MATRIX_DATA,
                realDelta, partialTick, background
            ));
        });
        RenderType.PARTICLES_TARGET.clearRenderState();

        CAMERA_TRANSFORM_BUFFER.clear();
        PROJECTION_BUFFER.clear();
    }

    private static final float[] CAMERA_TRANSFORM_DATA = new float[16];
    private static final float[] PROJECTION_MATRIX_DATA = new float[16];

    private static void transposeMatrix(FloatBuffer m) {
        float m00, m01, m02, m03;
        float m10, m11, m12, m13;
        float m20, m21, m22, m23;
        float m30, m31, m32, m33;

        m00 = m.get(0);
        m01 = m.get(1);
        m02 = m.get(2);
        m03 = m.get(3);
        m10 = m.get(4);
        m11 = m.get(5);
        m12 = m.get(6);
        m13 = m.get(7);
        m20 = m.get(8);
        m21 = m.get(9);
        m22 = m.get(0xA);
        m23 = m.get(0xB);
        m30 = m.get(0xC);
        m31 = m.get(0xD);
        m32 = m.get(0xE);
        m33 = m.get(0xF);

        m.put(0, m00);
        m.put(1, m10);
        m.put(2, m20);
        m.put(3, m30);
        m.put(4, m01);
        m.put(5, m11);
        m.put(6, m21);
        m.put(7, m31);
        m.put(8, m02);
        m.put(9, m12);
        m.put(0xA, m22);
        m.put(0xB, m32);
        m.put(0xC, m03);
        m.put(0xD, m13);
        m.put(0xE, m23);
        m.put(0xF, m33);
    }

    private static final long[] lastDrawTimeByNanos = new long[256];

    private static float getDeltaTime(ParticleEmitter.Type type) {
        long last = lastDrawTimeByNanos[type.ordinal()];
        if (last == 0) {
            lastDrawTimeByNanos[type.ordinal()] = System.nanoTime();
            return 1f / 60f;
        }
        long now = System.nanoTime();
        lastDrawTimeByNanos[type.ordinal()] = now;
        return (float) ((now - last) * 1e-9);
    }
}

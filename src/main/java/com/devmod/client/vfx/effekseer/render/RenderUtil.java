package com.devmod.client.vfx.effekseer.render;

import java.util.Optional;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_NEAREST;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindTexture;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;
public final class RenderUtil {
    public static final Minecraft MC = Minecraft.getInstance();

    private RenderUtil() {
    }

    public static void copyDepthSafely(RenderTarget from, RenderTarget to) {
        int read = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        to.copyDepthFrom(from);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
    }

    public static void copyDepthSafely(int src, int srcWidth, int srcHeight, RenderTarget target) {
        int readBackup = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int drawBackup = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, src);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        glBlitFramebuffer(0, 0, srcWidth, srcHeight, 0, 0, target.width, target.height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readBackup);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawBackup);
    }

    public static void copyDepthSafely(RenderTarget src, int target, int targetWidth, int targetHeight) {
        int readBackup = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int drawBackup = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, src.frameBufferId);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
        glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, targetWidth, targetHeight, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readBackup);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawBackup);
    }

    public static void copySafely(RenderTarget src, RenderTarget target) {
        int readBackup = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int drawBackup = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, src.frameBufferId);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, target.width, target.height,
            GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readBackup);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawBackup);
    }

    public static void runPixelStoreCodeSafely(Runnable code) {
        int packAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        int unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        int unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        int unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        int unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        code.run();
        glPixelStorei(GL_PACK_ALIGNMENT, packAlignment);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
    }

    public static void runPixelStoreCodeHealthily(Runnable code) {
        runPixelStoreCodeSafely(() -> {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            code.run();
        });
    }

    public static boolean isReloadingResourcePacks() {
        return false;
    }

    public static void refreshBackgroundFrameBuffer() {
        RenderStateCapture.ensureSizedToMain();
    }

    public static Optional<RenderTarget> prepareBackgroundBuffer() {
        RenderTarget background = RenderStateCapture.DISTORTION_BACKGROUND;
        copySafely(MC.getMainRenderTarget(), background);
        return Optional.of(background);
    }

    public static void pasteToCurrentDepthFrom(RenderTarget source) {
        int frameBuffer = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        var window = MC.getWindow();
        copyDepthSafely(source, frameBuffer, window.getWidth(), window.getHeight());
    }

    public static void copyCurrentDepthTo(RenderTarget target) {
        int frameBuffer = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        var window = MC.getWindow();
        copyDepthSafely(frameBuffer, window.getWidth(), window.getHeight(), target);
    }

    public static void runFrameBufferCodeSafely(Runnable code) {
        int readBackup = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int drawBackup = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        code.run();
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readBackup);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawBackup);
        glBindTexture(GL_TEXTURE_2D, texture);
    }
}

package com.devmod.client.vfx.effekseer.render;

import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

import net.minecraft.client.Minecraft;

public final class RenderStateCapture {
    private static final Set<RenderTarget> TRACKED_FBS = new LinkedHashSet<>(1);

    public static final RenderTarget DISTORTION_BACKGROUND = create();

    private RenderStateCapture() {
    }

    private static RenderTarget create() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        RenderTarget result = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        TRACKED_FBS.add(result);
        return result;
    }

    public static void ensureSizedToMain() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        for (RenderTarget fb : TRACKED_FBS) {
            if (fb.width != main.width || fb.height != main.height) {
                fb.resize(main.width, main.height, Minecraft.ON_OSX);
            }
        }
    }
}

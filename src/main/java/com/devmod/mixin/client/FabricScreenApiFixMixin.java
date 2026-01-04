package com.devmod.mixin.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.devmod.client.endurance.ClientQuestCache;

/**
 * Workaround for Fabric Screen API bug in forgified-fabric-api.
 * <p>
 * The bug: Fabric's MinecraftClientMixin calls ScreenEvents.remove(this.screen)
 * without null-checking, causing NPE when screen is null during player death.
 * <p>
 * This mixin runs BEFORE Fabric's (priority 500 vs default 1000) and
 * temporarily sets a dummy screen if null, preventing the NPE.
 */
@Mixin(value = Minecraft.class, priority = 500)
@SuppressWarnings({"UnusedMethod", "UnusedVariable"}) // Mixin methods are invoked via bytecode injection
public class FabricScreenApiFixMixin {

    @Shadow
    @Nullable
    public Screen screen;

    // Lazy-initialized dummy screen to avoid initialization order issues
    @Unique
    @Nullable
    private static Screen devmod$dummyScreen = null;

    @Unique
    private boolean devmod$usingDummyScreen = false;

    @Unique
    @SuppressWarnings("null") // Component.literal() is always non-null
    private Screen devmod$getDummyScreen() {
        if (devmod$dummyScreen == null) {
            devmod$dummyScreen = new Screen(Component.literal("")) {
                @Override
                protected void init() {
                    // Dummy screen - never actually displayed
                }

                @Override
                public void render(@Nonnull net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    // Never render anything - this is just a placeholder
                }

                @Override
                public void renderBackground(@Nonnull net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    // Never render background
                }
            };
        }
        return devmod$dummyScreen;
    }

    /**
     * Before Fabric's mixin runs, ensure screen is not null.
     * This prevents ScreenEvents.remove(null) from throwing NPE.
     *
     * Also intercepts vanilla DeathScreen during active quests - the server
     * will send our custom QuestDeathScreen via network packet instead.
     */
    @Inject(
        method = "setScreen",
        at = @At("HEAD"),
        cancellable = true
    )
    private void devmod$preventNullScreenForFabric(Screen newScreen, CallbackInfo ci) {
        // Suppress vanilla DeathScreen when player is in an active Endurance Quest
        // Our custom QuestDeathScreen is sent via network packet from server
        if (newScreen instanceof DeathScreen && ClientQuestCache.hasActiveQuest()) {
            ci.cancel();
            return;
        }

        if (this.screen == null) {
            // Temporarily set a dummy screen so Fabric's ScreenEvents.remove() doesn't NPE
            this.screen = devmod$getDummyScreen();
            this.devmod$usingDummyScreen = true;
        }
    }

    /**
     * After all HEAD injections, restore null if we used dummy.
     * This runs before the actual method body starts processing.
     */
    @Inject(
        method = "setScreen",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;", ordinal = 0)
    )
    private void devmod$restoreNullScreen(Screen newScreen, CallbackInfo ci) {
        if (this.devmod$usingDummyScreen) {
            if (this.screen == devmod$dummyScreen) {
                this.screen = null;
            }
            this.devmod$usingDummyScreen = false;
        }
    }
}

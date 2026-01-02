package com.devmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.bridge.ClientUiBridge;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ItemEditorScreen;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class ClientUiBridgeImpl implements ClientUiBridge {

    /**
     * Initialize and register this bridge.
     * Called during client mod initialization.
     */
    public static void init() {
        ClientUiBridge.register(new ClientUiBridgeImpl());
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    public void openSettings() {
        Minecraft mc = mc();
        mc.setScreen(new com.devmod.client.ui.unified.UnifiedSettingsScreen(mc.screen));
    }

    @Override
    public void openRadialMenu() {
        mc().setScreen(new com.devmod.client.ui.radial.RadialMenuScreen());
    }

    @Override
    public void openTestingHub() {
        mc().setScreen(new com.devmod.client.ui.hub.TestingHub());
    }

    @Override
    public void openItemEditor() {
        var player = mc().player;
        if (player != null) {
            mc().setScreen(new ItemEditorScreen(
                player.getMainHandItem(),
                EditorStartTab.GENERAL));
        }
    }

    @Override
    public void openMobConfig() {
        Minecraft mc = mc();
        if (mc.hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof Mob mob) {
                mc.setScreen(new com.devmod.client.ui.screens.MobConfigScreen(mob));
                return;
            }
        }
        var player = mc.player;
        if (player != null) {
            player.displayClientMessage(I18n.translate("devmod.network.target_not_found"), true);
        }
    }

    @Override
    public void openTelemetryDashboard() {
        mc().setScreen(new com.devmod.client.ui.screens.TelemetryDashboardScreen(mc().screen));
    }

    @Override
    public void openWelcomeScreen() {
        mc().setScreen(new com.devmod.client.ui.WelcomeScreen());
    }

    @Override
    public void openArenaQuickTestWizard() {
        mc().setScreen(new com.devmod.client.ui.wizard.QuickTestWizard());
    }

    @Override
    public void openEnduranceQuestScreen(String templateId) {
        mc().setScreen(new com.devmod.client.endurance.EnduranceQuestScreen());
    }

    @Override
    public void openPartyScreen() {
        mc().setScreen(new com.devmod.client.party.PartyScreen());
    }

    @Override
    public void toggleQuickHelp() {
        com.devmod.client.overlay.QuickHelpOverlay.toggle();
    }

    @Override
    public void toggleDebugOverlay() {
        DebugOverlay.toggle();
    }

    @Override
    public void showNotification(String message, NotificationType type) {
        var player = mc().player;
        if (player != null) {
            Component component = Component.literal("[" + type.name() + "] " + message);
            java.util.Objects.requireNonNull(component);
            player.displayClientMessage(
                component,
                true
            );
        }
    }

    @Override
    public boolean isScreenOpen() {
        return mc().screen != null;
    }

    @Override
    public void closeScreen() {
        mc().setScreen(null);
    }
}

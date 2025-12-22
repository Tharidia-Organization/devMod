package com.frenkvs.devmod.client;

import com.frenkvs.devmod.bridge.ClientUiBridge;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.editor.ItemEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side implementation of {@link ClientUiBridge}.
 *
 * <p>This class is only loaded on the client and provides actual
 * screen opening functionality using Minecraft.getInstance().
 */
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
        mc().setScreen(new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(null));
    }

    @Override
    public void openRadialMenu() {
        mc().setScreen(new com.frenkvs.devmod.ui.radial.RadialMenuScreenV3());
    }

    @Override
    public void openTestingHub() {
        mc().setScreen(new com.frenkvs.devmod.ui.hub.TestingHub());
    }

    @Override
    public void openItemEditor() {
        if (mc().player != null) {
            mc().setScreen(new ItemEditorScreen(
                mc().player.getMainHandItem(),
                EditorStartTab.GENERAL));
        }
    }

    @Override
    public void openMobConfig() {
        mc().setScreen(new com.frenkvs.devmod.MobConfigScreen(null));
    }

    @Override
    public void openTelemetryDashboard() {
        mc().setScreen(new com.frenkvs.devmod.TelemetryDashboardScreen(null));
    }

    @Override
    public void openWelcomeScreen() {
        mc().setScreen(new com.frenkvs.devmod.ui.WelcomeScreen());
    }

    @Override
    public void openArenaQuickTestWizard() {
        mc().setScreen(new com.frenkvs.devmod.ui.wizard.QuickTestWizard());
    }

    @Override
    public void openEnduranceQuestScreen(String templateId) {
        mc().setScreen(new com.frenkvs.devmod.endurance.EnduranceQuestScreen());
    }

    @Override
    public void openPartyScreen() {
        mc().setScreen(new com.frenkvs.devmod.party.PartyScreen());
    }

    @Override
    public void toggleQuickHelp() {
        com.frenkvs.devmod.hud.QuickHelpOverlay.toggle();
    }

    @Override
    public void toggleDebugOverlay() {
        // Cycle through debug overlay modes
        com.frenkvs.devmod.ui.DebugOverlay.cycleMode();
    }

    @Override
    public void showNotification(String message, NotificationType type) {
        var player = mc().player;
        if (player != null) {
            player.displayClientMessage(
                Component.literal("[" + type.name() + "] " + message),
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

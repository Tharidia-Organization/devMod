package com.devmod.client;

import com.devmod.bridge.ClientUiBridge;
import com.devmod.ui.editor.EditorStartTab;
import com.devmod.ui.editor.ItemEditorScreen;
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
        mc().setScreen(new com.devmod.ui.unified.UnifiedSettingsScreen(null));
    }

    @Override
    public void openRadialMenu() {
        mc().setScreen(new com.devmod.ui.radial.RadialMenuScreenV3());
    }

    @Override
    public void openTestingHub() {
        mc().setScreen(new com.devmod.ui.hub.TestingHub());
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
        mc().setScreen(new com.devmod.ui.screens.MobConfigScreen(null));
    }

    @Override
    public void openTelemetryDashboard() {
        mc().setScreen(new com.devmod.ui.screens.TelemetryDashboardScreen(null));
    }

    @Override
    public void openWelcomeScreen() {
        mc().setScreen(new com.devmod.ui.WelcomeScreen());
    }

    @Override
    public void openArenaQuickTestWizard() {
        mc().setScreen(new com.devmod.ui.wizard.QuickTestWizard());
    }

    @Override
    public void openEnduranceQuestScreen(String templateId) {
        mc().setScreen(new com.devmod.endurance.EnduranceQuestScreen());
    }

    @Override
    public void openPartyScreen() {
        mc().setScreen(new com.devmod.party.PartyScreen());
    }

    @Override
    public void toggleQuickHelp() {
        com.devmod.overlay.QuickHelpOverlay.toggle();
    }

    @Override
    public void toggleDebugOverlay() {
        // Debug overlay mode cycling not implemented
        // TODO: Implement via com.devmod.ui.editor.debug.DebugOverlay when needed
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

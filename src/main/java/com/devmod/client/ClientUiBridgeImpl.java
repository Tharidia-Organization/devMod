package com.devmod.client;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.bridge.ClientUiBridge;
import com.devmod.client.compat.mods.yacl.YaclCompat;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ItemEditorScreen;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class ClientUiBridgeImpl implements ClientUiBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientUiBridgeImpl.class);

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
        Screen parent = mc.screen;

        // Try YACL-based settings if available and preferred
        Screen yaclScreen = tryBuildYaclSettings(parent);
        if (yaclScreen != null) {
            LOGGER.debug("[ClientUiBridge] Using YACL settings screen");
            mc.setScreen(yaclScreen);
            return;
        }

        // Fall back to native settings screen
        mc.setScreen(new com.devmod.client.ui.unified.UnifiedSettingsScreen(parent));
    }

    /**
     * Attempt to build a YACL-based settings screen.
     * Uses YaclCompat API methods when YACL is available and preferred.
     *
     * @param parent Parent screen
     * @return YACL screen if successful, null otherwise
     */
    @Nullable
    private Screen tryBuildYaclSettings(Screen parent) {
        // Check if YACL should be used
        if (!YaclCompat.isApiAvailable() || !YaclCompat.shouldPreferYacl()) {
            LOGGER.debug("[ClientUiBridge] YACL status: {}", YaclCompat.getStatusSummary());
            return null;
        }

        // Try to build YACL screen with DevMod categories
        final boolean[] categoryAdded = {false};
        Screen screen = YaclCompat.buildConfigScreen(
            parent,
            Component.translatable("devmod.settings.title"),
            builder -> {
                // Create a simple category for demonstration
                Object categoryBuilder = YaclCompat.createCategoryBuilder();
                if (categoryBuilder == null) {
                    return;
                }

                try {
                    // Configure category name
                    var nameMethod = categoryBuilder.getClass().getMethod("name", Component.class);
                    categoryBuilder = nameMethod.invoke(categoryBuilder,
                        Component.translatable("devmod.settings.category.general"));

                    // Add tooltip/description
                    Object description = YaclCompat.createDescription(
                        Component.translatable("devmod.settings.category.general.description"));
                    if (description != null) {
                        var tooltipMethod = categoryBuilder.getClass().getMethod("tooltip", description.getClass());
                        categoryBuilder = tooltipMethod.invoke(categoryBuilder, description);
                    }

                    // Build category and add to builder
                    var buildMethod = categoryBuilder.getClass().getMethod("build");
                    Object category = buildMethod.invoke(categoryBuilder);

                    var categoryMethod = builder.getClass().getMethod("category", category.getClass());
                    categoryMethod.invoke(builder, category);
                    categoryAdded[0] = true;

                } catch (Exception e) {
                    LOGGER.debug("[ClientUiBridge] YACL category configuration failed: {}", e.getMessage());
                }
            }
        );
        if (screen == null || !categoryAdded[0]) {
            LOGGER.debug("[ClientUiBridge] YACL screen build incomplete, falling back");
            return null;
        }
        return screen;
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

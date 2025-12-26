package com.devmod.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public interface ClientUiBridge {

    Logger LOGGER = LoggerFactory.getLogger(ClientUiBridge.class);

    // Singleton holder with lazy initialization
    class Holder {
        private static ClientUiBridge INSTANCE = new NoOpBridge();
    }

    /**
     * Get the current bridge implementation.
     */
    static ClientUiBridge get() {
        return Holder.INSTANCE;
    }

    /**
     * Register the client-side implementation.
     * Called from client mod initialization.
     */
    static void register(ClientUiBridge bridge) {
        Holder.INSTANCE = bridge;
        LOGGER.info("[ClientUiBridge] Registered client implementation: {}", bridge.getClass().getSimpleName());
    }

    // ========== Screen Operations ==========

    /**
     * Open the unified settings screen.
     */
    void openSettings();

    /**
     * Open the radial menu.
     */
    void openRadialMenu();

    /**
     * Open the testing hub.
     */
    void openTestingHub();

    /**
     * Open the item editor for current held item.
     */
    void openItemEditor();

    /**
     * Open the mob config screen.
     */
    void openMobConfig();

    /**
     * Open the telemetry dashboard.
     */
    void openTelemetryDashboard();

    /**
     * Open the welcome screen.
     */
    void openWelcomeScreen();

    /**
     * Open the arena quick test wizard.
     */
    void openArenaQuickTestWizard();

    /**
     * Open the endurance quest screen.
     * @param templateId the arena template ID
     */
    void openEnduranceQuestScreen(String templateId);

    /**
     * Open the party screen.
     */
    void openPartyScreen();

    // ========== HUD Operations ==========

    /**
     * Toggle quick help overlay.
     */
    void toggleQuickHelp();

    /**
     * Toggle debug overlay.
     */
    void toggleDebugOverlay();

    /**
     * Show a notification toast.
     */
    void showNotification(String message, NotificationType type);

    enum NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    // ========== Query Operations ==========

    /**
     * Check if any screen is currently open.
     */
    boolean isScreenOpen();

    /**
     * Close the current screen.
     */
    void closeScreen();

    // ========== No-Op Implementation for Server ==========

    /**
     * No-op implementation used on dedicated servers.
     * All operations are silently ignored.
     */
    class NoOpBridge implements ClientUiBridge {
        @Override
        public void openSettings() {}

        @Override
        public void openRadialMenu() {}

        @Override
        public void openTestingHub() {}

        @Override
        public void openItemEditor() {}

        @Override
        public void openMobConfig() {}

        @Override
        public void openTelemetryDashboard() {}

        @Override
        public void openWelcomeScreen() {}

        @Override
        public void openArenaQuickTestWizard() {}

        @Override
        public void openEnduranceQuestScreen(String templateId) {}

        @Override
        public void openPartyScreen() {}

        @Override
        public void toggleQuickHelp() {}

        @Override
        public void toggleDebugOverlay() {}

        @Override
        public void showNotification(String message, NotificationType type) {
            // On server, log instead of displaying
            LOGGER.debug("[Server Notification] [{}] {}", type, message);
        }

        @Override
        public boolean isScreenOpen() {
            return false;
        }

        @Override
        public void closeScreen() {}
    }
}

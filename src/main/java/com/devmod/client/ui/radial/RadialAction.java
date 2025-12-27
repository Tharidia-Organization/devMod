package com.devmod.client.ui.radial;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ActionKeybindRegistry;
import com.devmod.actions.client.ClientActionContexts;

public abstract class RadialAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(RadialAction.class);

    public abstract void execute();
    public abstract Component getLabel();
    public abstract String getDescription();

    /**
     * Whether this action is currently available for execution.
     * For backward compatibility, returns true by default.
     */
    public boolean isAvailable() {
        return true;
    }

    /**
     * Whether this action should be visible in the menu.
     * Separate from isAvailable() to support visibility gating.
     * Default implementation returns isAvailable() for backward compat.
     */
    public boolean isVisible() {
        return isAvailable();
    }

    /**
     * Whether the execution precondition would pass.
     * Used for grayed-out rendering (visible but not executable).
     * Default implementation returns isAvailable().
     */
    public boolean canExecute() {
        return isAvailable();
    }

    /**
     * Whether this action represents a toggleable state (vs one-shot action)
     */
    public boolean isToggle() {
        return false;
    }

    /**
     * For toggleable actions, returns current state
     */
    public boolean isActive() {
        return false;
    }

    /**
     * Icon for the action (ItemStack or null for emoji fallback)
     */
    @Nullable
    public ItemStack getIconStack() {
        return null;
    }

    /**
     * Emoji fallback icon
     */
    public String getIconEmoji() {
        return "*";
    }

    /**
     * Check if this action navigates to a subcategory.
     * Override in subcategory actions.
     */
    public boolean isSubcategoryAction() {
        return false;
    }

    /**
     * Get the subcategory this action navigates to.
     * Returns null unless this is a subcategory action.
     */
    @Nullable
    public RadialCategory getSubcategory() {
        return null;
    }

    /**
     * Return backing registry id when this action is registry-backed.
     */
    @Nullable
    public String getRegistryId() {
        return null;
    }

    // ============================================================
    // Factory methods for common action types
    // ============================================================

    /**
     * Create a toggle action for boolean state
     */
    public static RadialAction toggle(String name, String description, String emoji,
                                       BooleanSupplier getter, Consumer<Boolean> setter) {
        return toggle(name, description, emoji, null, getter, setter);
    }

    /**
     * Create a toggle action with ItemStack icon
     */
    public static RadialAction toggle(String name, String description, String emoji,
                                       @Nullable ItemStack icon, BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleAction(name, description, emoji, icon, getter, setter);
    }

    /**
     * Create a command action.
     * @deprecated Use {@link #registry(String)} with an action registered in ActionRegistry instead.
     *             CommandAction sends commands directly via chat which bypasses the action system.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialAction command(String name, String description, String emoji, String command) {
        return command(name, description, emoji, null, command);
    }

    /**
     * Create a command action with ItemStack icon.
     * @deprecated Use {@link #registry(String)} with an action registered in ActionRegistry instead.
     *             CommandAction sends commands directly via chat which bypasses the action system.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialAction command(String name, String description, String emoji,
                                        @Nullable ItemStack icon, String command) {
        return new CommandAction(name, description, emoji, icon, command);
    }

    /**
     * Create a screen-opening action
     */
    public static RadialAction screen(String name, String description, String emoji,
                                       java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory) {
        return screen(name, description, emoji, null, screenFactory);
    }

    /**
     * Create a screen-opening action with ItemStack icon
     */
    public static RadialAction screen(String name, String description, String emoji,
                                       @Nullable ItemStack icon,
                                       java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory) {
        return new ScreenAction(name, description, emoji, icon, screenFactory);
    }

    /**
     * Create a keybind simulation action
     */
    public static RadialAction keybind(String name, String description, String emoji, int keyCode) {
        return keybind(name, description, emoji, null, keyCode);
    }

    /**
     * Create a keybind simulation action with ItemStack icon
     */
    public static RadialAction keybind(String name, String description, String emoji,
                                        @Nullable ItemStack icon, int keyCode) {
        return new KeybindAction(name, description, emoji, icon, keyCode);
    }

    /**
     * Create a custom runnable action.
     * @deprecated Use {@link #registry(String)} with an action registered in ActionRegistry instead.
     *             CustomAction uses opaque Runnables which bypass telemetry and precondition checks.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialAction custom(String name, String description, String emoji, Runnable action) {
        return custom(name, description, emoji, null, action);
    }

    /**
     * Create a custom runnable action with ItemStack icon.
     * @deprecated Use {@link #registry(String)} with an action registered in ActionRegistry instead.
     *             CustomAction uses opaque Runnables which bypass telemetry and precondition checks.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialAction custom(String name, String description, String emoji,
                                       @Nullable ItemStack icon, Runnable action) {
        return new CustomAction(name, description, emoji, icon, action);
    }

    /**
     * Create a subcategory navigation action
     */
    public static RadialAction subcategory(String name, String description, String emoji,
                                            RadialCategory subcategory) {
        return new SubcategoryAction(name, description, emoji, subcategory);
    }

    /**
     * Create an action backed by the central ActionRegistry.
     */
    public static RadialAction registry(String actionId) {
        return new RegistryAction(actionId);
    }

    // ============================================================
    // Action implementations
    // ============================================================

    /**
     * Toggle action for boolean state
     */
    private static class ToggleAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        @Nullable
        private final ItemStack icon;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        ToggleAction(String name, String description, String emoji, @Nullable ItemStack icon,
                     BooleanSupplier getter, Consumer<Boolean> setter) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void execute() {
            setter.accept(!getter.getAsBoolean());
        }

        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name)));
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean isToggle() {
            return true;
        }

        @Override
        public boolean isActive() {
            return getter.getAsBoolean();
        }

        @Override
        @Nullable
        public ItemStack getIconStack() {
            return icon;
        }

        @Override
        public String getIconEmoji() {
            return emoji;
        }
    }

    /**
     * Command execution action
     * @deprecated Use {@link RegistryAction} with an action registered in ActionRegistry instead.
     */
    @Deprecated
    private static class CommandAction extends RadialAction {
        @Deprecated private final String name;
        @Deprecated private final String description;
        @Deprecated private final String emoji;
        @Deprecated @Nullable private final ItemStack icon;
        @Deprecated private final String command;

        @Deprecated
        CommandAction(String name, String description, String emoji, @Nullable ItemStack icon, String command) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.command = command.startsWith("/") ? command : "/" + command;
        }

        @Deprecated
        @Override
        public void execute() {
            Minecraft mc = Minecraft.getInstance();
            var player = mc.player;
            if (player != null && mc.getConnection() != null) {
                if (command.startsWith("/")) {
                    player.connection.sendCommand(Objects.requireNonNull(command.substring(1)));
                } else {
                    player.connection.sendCommand(Objects.requireNonNull(command));
                }
            }
        }

        @Deprecated
        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name)));
        }

        @Deprecated
        @Override
        public String getDescription() {
            return description + "\n§7Command: §f" + command;
        }

        @Deprecated
        @Override
        @Nullable
        public ItemStack getIconStack() {
            return icon != null ? icon : new ItemStack(Objects.requireNonNull(Items.COMMAND_BLOCK));
        }

        @Deprecated
        @Override
        public String getIconEmoji() {
            return emoji;
        }
    }

    /**
     * Screen navigation action
     */
    private static class ScreenAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        @Nullable
        private final ItemStack icon;
        private final java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory;

        ScreenAction(String name, String description, String emoji, @Nullable ItemStack icon,
                     java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.screenFactory = screenFactory;
        }

        @Override
        public void execute() {
            Minecraft.getInstance().tell(() -> {
                try {
                    Minecraft.getInstance().setScreen(screenFactory.get());
                } catch (Exception e) {
                    LOGGER.error("[RadialAction] Failed to open screen: {}", e.getMessage());
                    Minecraft.getInstance().setScreen(null);
                }
            });
        }

        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name)));
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        @Nullable
        public ItemStack getIconStack() {
            return icon;
        }

        @Override
        public String getIconEmoji() {
            return emoji;
        }
    }

    /**
     * Keybind simulation action
     */
    private static class KeybindAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        @Nullable
        private final ItemStack icon;
        private final int keyCode;

        KeybindAction(String name, String description, String emoji, @Nullable ItemStack icon, int keyCode) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.keyCode = keyCode;
        }

        @Override
        public void execute() {
            // Simulate key press through Minecraft's input system
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> {
                // Send key event to the game
                long window = mc.getWindow().getWindow();
                org.lwjgl.glfw.GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {});
                // Note: Full key simulation would need more complex implementation
                // This is a placeholder for the concept
            });
        }

        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name)));
        }

        @Override
    public String getDescription() {
        String keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
        return description + "\n§7Key: §f" + (keyName != null ? keyName.toUpperCase(Locale.ROOT) : "KEY_" + keyCode);
    }

        @Override
        @Nullable
        public ItemStack getIconStack() {
            return icon;
        }

        @Override
        public String getIconEmoji() {
            return emoji;
        }
    }

    /**
     * Custom runnable action
     * @deprecated Use {@link RegistryAction} with an action registered in ActionRegistry instead.
     */
    @Deprecated
    private static class CustomAction extends RadialAction {
        @Deprecated private final String name;
        @Deprecated private final String description;
        @Deprecated private final String emoji;
        @Deprecated @Nullable private final ItemStack icon;
        @Deprecated private final Runnable action;

        @Deprecated
        CustomAction(String name, String description, String emoji, @Nullable ItemStack icon, Runnable action) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.action = action;
        }

        @Deprecated
        @Override
        public void execute() {
            action.run();
        }

        @Deprecated
        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name)));
        }

        @Deprecated
        @Override
        public String getDescription() {
            return description;
        }

        @Deprecated
        @Override
        @Nullable
        public ItemStack getIconStack() {
            return icon;
        }

        @Deprecated
        @Override
        public String getIconEmoji() {
            return emoji;
        }
    }

    /**
     * Subcategory navigation action
     */
    private static class SubcategoryAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        private final RadialCategory subcategory;

        SubcategoryAction(String name, String description, String emoji, RadialCategory subcategory) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.subcategory = subcategory;
        }

        @Override
        public void execute() {
            // Navigation is handled by the menu screen
        }

        @Override
        public Component getLabel() {
            return Objects.requireNonNull(Component.literal(Objects.requireNonNull(name) + " ->"));
        }

        @Override
        public String getDescription() {
            return description + "\n§7Click to expand";
        }

        @Override
        public String getIconEmoji() {
            return emoji;
        }

        @Override
        public RadialCategory getSubcategory() {
            return subcategory;
        }

        @Override
        public boolean isSubcategoryAction() {
            return true;
        }
    }

    /**
     * Registry-backed action wrapper.
     */
    private static class RegistryAction extends RadialAction {
        private final String actionId;

        RegistryAction(String actionId) {
            this.actionId = actionId;
        }

        @Override
        public void execute() {
            ActionRegistry.invoke(actionId, ClientActionContexts.forRadial());
        }

        @Override
        public Component getLabel() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return Component.literal(Objects.requireNonNull(actionId, "actionId"));
            }
            return action.getLabel();
        }

        @Override
        public String getDescription() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return actionId;
            }
            String description = action.getDescription().getString();
            String commandHint = action.getCommandHint();
            if (commandHint != null && !commandHint.isBlank()) {
                description += "\n§7Command: §f/" + commandHint;
            }
            ActionKeybindRegistry.KeybindHint keybindHint = ActionKeybindRegistry.getKeybindHint(actionId);
            if (keybindHint != null) {
                String keyName = keybindHint.keyMapping().getTranslatedKeyMessage().getString();
                String modifier = keybindHint.modifierHint();
                String keyLabel = modifier != null && !modifier.isBlank()
                    ? modifier + "+" + keyName
                    : keyName;
                description += "\n§7Key: §f" + keyLabel;
            }
            return description;
        }

        /**
         * Returns true if this action is visible in the menu.
         * Uses the visibility predicate, NOT the execution precondition.
         * This implements Scenario 9: hidden actions for non-admins.
         */
        @Override
        public boolean isVisible() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return false;
            }
            return action.isVisible(ClientActionContexts.forRadial());
        }

        /**
         * Returns true if this action can be executed.
         * Uses the execution precondition.
         * Note: This is checked at invoke time for proper error feedback.
         */
        @Override
        public boolean isAvailable() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return false;
            }
            // For visibility gating: use isVisible()
            // For execution: precondition is checked at invoke time with proper error feedback
            // Return visibility here to maintain backward compat with RadialMenuItem.isVisible()
            return action.isVisible(ClientActionContexts.forRadial());
        }

        /**
         * Check if precondition would pass (for grayed-out rendering).
         */
        @Override
        public boolean canExecute() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return false;
            }
            return action.getPrecondition().test(ClientActionContexts.forRadial());
        }

        @Override
        public boolean isToggle() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            return action != null && action.isToggle();
        }

        @Override
        public boolean isActive() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return false;
            }
            return action.isActive(ClientActionContexts.forRadial());
        }

        @Override
        @Nullable
        public ItemStack getIconStack() {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action == null) {
                return null;
            }
            return action.getIconStack();
        }

        @Override
        public String getRegistryId() {
            return actionId;
        }
    }
}

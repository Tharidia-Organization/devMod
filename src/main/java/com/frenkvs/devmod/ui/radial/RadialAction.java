package com.frenkvs.devmod.ui.radial;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Modular action system for radial menu items.
 * Supports commands, key bindings, item usage, screen navigation, and custom actions.
 */
public abstract class RadialAction {

    public abstract void execute();
    public abstract Component getLabel();
    public abstract String getDescription();

    /**
     * Whether this action is currently available
     */
    public boolean isAvailable() {
        return true;
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
        return "●";
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
     * Create a command action
     */
    public static RadialAction command(String name, String description, String emoji, String command) {
        return command(name, description, emoji, null, command);
    }

    /**
     * Create a command action with ItemStack icon
     */
    public static RadialAction command(String name, String description, String emoji,
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
     * Create a custom runnable action
     */
    public static RadialAction custom(String name, String description, String emoji, Runnable action) {
        return custom(name, description, emoji, null, action);
    }

    /**
     * Create a custom runnable action with ItemStack icon
     */
    public static RadialAction custom(String name, String description, String emoji,
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
        private final ItemStack icon;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        ToggleAction(String name, String description, String emoji, ItemStack icon,
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
            return Component.literal(name);
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
     */
    private static class CommandAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        private final ItemStack icon;
        private final String command;

        CommandAction(String name, String description, String emoji, ItemStack icon, String command) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.command = command.startsWith("/") ? command : "/" + command;
        }

        @Override
        public void execute() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getConnection() != null) {
                if (command.startsWith("/")) {
                    mc.player.connection.sendCommand(command.substring(1));
                } else {
                    mc.player.connection.sendCommand(command);
                }
            }
        }

        @Override
        public Component getLabel() {
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            return description + "\n§7Command: §f" + command;
        }

        @Override
        public ItemStack getIconStack() {
            return icon != null ? icon : new ItemStack(Items.COMMAND_BLOCK);
        }

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
        private final ItemStack icon;
        private final java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory;

        ScreenAction(String name, String description, String emoji, ItemStack icon,
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
                    System.err.println("[DevMod] Failed to open screen: " + e.getMessage());
                    Minecraft.getInstance().setScreen(null);
                }
            });
        }

        @Override
        public Component getLabel() {
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
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
        private final ItemStack icon;
        private final int keyCode;

        KeybindAction(String name, String description, String emoji, ItemStack icon, int keyCode) {
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
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            String keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
            return description + "\n§7Key: §f" + (keyName != null ? keyName.toUpperCase() : "KEY_" + keyCode);
        }

        @Override
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
     */
    private static class CustomAction extends RadialAction {
        private final String name;
        private final String description;
        private final String emoji;
        private final ItemStack icon;
        private final Runnable action;

        CustomAction(String name, String description, String emoji, ItemStack icon, Runnable action) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.icon = icon;
            this.action = action;
        }

        @Override
        public void execute() {
            action.run();
        }

        @Override
        public Component getLabel() {
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ItemStack getIconStack() {
            return icon;
        }

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
            return Component.literal(name + " →");
        }

        @Override
        public String getDescription() {
            return description + "\n§7Click to expand";
        }

        @Override
        public String getIconEmoji() {
            return emoji;
        }

        public RadialCategory getSubcategory() {
            return subcategory;
        }

        /**
         * Check if this action navigates to a subcategory
         */
        public boolean isSubcategory() {
            return true;
        }
    }
}

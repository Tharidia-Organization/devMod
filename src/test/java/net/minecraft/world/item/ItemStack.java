package net.minecraft.world.item;

/**
 * Minimal test stub for ItemStack to allow JVM unit tests without full Minecraft mappings.
 * This class intentionally contains no game logic and only exists for compilation in tests.
 */
public class ItemStack {
    private final String name;
    private final boolean empty;

    public ItemStack() {
        this("Test Item", false);
    }

    public ItemStack(String name) {
        this(name, false);
    }

    public ItemStack(String name, boolean empty) {
        this.name = name;
        this.empty = empty;
    }

    public boolean isEmpty() {
        return empty;
    }

    public ItemStack copy() {
        return new ItemStack(name, empty);
    }

    public static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.name.equals(b.name);
    }

    public net.minecraft.network.chat.Component getHoverName() {
        return net.minecraft.network.chat.Component.literal(name);
    }
}

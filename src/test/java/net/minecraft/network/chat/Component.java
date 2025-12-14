package net.minecraft.network.chat;

/**
 * Minimal Component stub for unit tests. Only supports returning the raw text value.
 */
public interface Component {
    String getString();

    /**
     * Convenience factory mirroring Component.literal usage.
     */
    static Component literal(String text) {
        return () -> text;
    }
}

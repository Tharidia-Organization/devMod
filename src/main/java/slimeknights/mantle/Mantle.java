package slimeknights.mantle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal Mantle bootstrap used for model utilities.
 */
public final class Mantle {
    public static final String MOD_ID = "mantle";
    public static final Logger logger = LogManager.getLogger(MOD_ID);

    private Mantle() {}

    public static ResourceLocation getResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String makeDescriptionId(String type, String name) {
        return type + "." + MOD_ID + "." + name;
    }
}

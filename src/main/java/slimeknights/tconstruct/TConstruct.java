package slimeknights.tconstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resources.ResourceLocation;

/** Minimal TConstruct bootstrap for model utilities. */
public final class TConstruct {
  public static final String MOD_ID = "tconstruct";
  public static final Logger LOG = LogManager.getLogger(MOD_ID);

  private TConstruct() {}

  public static ResourceLocation getResource(String path) {
    return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
  }

  public static String makeTranslationKey(String type, String name) {
    return type + "." + MOD_ID + "." + name;
  }

  public static String resourceString(String path) {
    return MOD_ID + ":" + path;
  }
}

package slimeknights.tconstruct.library.materials.definition;

import slimeknights.tconstruct.TConstruct;

/** Minimal material interface for rendering. */
public interface IMaterial {
  MaterialVariantId UNKNOWN_ID = MaterialVariantId.create(TConstruct.MOD_ID, "unknown", "");

  MaterialVariantId getIdentifier();
}

package slimeknights.tconstruct.library.recipe.worktable;

import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import java.util.Collections;
import java.util.Set;

/** Stub for modifier set lookups used by tool models. */
public final class ModifierSetWorktableRecipe {
  private ModifierSetWorktableRecipe() {}

  public static Set<ModifierId> getModifierSet(ModDataNBT data, ResourceLocation key) {
    return Collections.emptySet();
  }
}

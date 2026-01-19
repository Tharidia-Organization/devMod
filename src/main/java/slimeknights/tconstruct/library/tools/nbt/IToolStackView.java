package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.world.item.Item;

/** Minimal read-only view of tool data for model rendering. */
public interface IToolStackView {
  Item getItem();

  ModifierNBT getModifiers();

  ModifierNBT getUpgrades();

  ModDataNBT getPersistentData();
}

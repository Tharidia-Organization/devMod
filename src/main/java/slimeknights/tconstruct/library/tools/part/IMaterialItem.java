package slimeknights.tconstruct.library.tools.part;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/** Interface for items that store a material. */
public interface IMaterialItem extends ItemLike {
  MaterialVariantId getMaterial(ItemStack stack);

  static MaterialVariantId getMaterialFromStack(ItemStack stack) {
    if (stack.getItem() instanceof IMaterialItem item) {
      return item.getMaterial(stack);
    }
    return IMaterial.UNKNOWN_ID;
  }
}

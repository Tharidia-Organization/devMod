package slimeknights.tconstruct.library.client.model.tools;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Helper class for delegating to nested overrides while also doing item-specific overrides. */
public class NestedOverrides extends ItemOverrides {
  private static boolean ignoreNested = false;

  private final ItemOverrides nested;

  public NestedOverrides(ItemOverrides nested) {
    this.nested = nested;
  }

  @Override
  @Nullable
  public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
    if (!ignoreNested) {
      BakedModel overridden = nested.resolve(originalModel, stack, world, entity, seed);
      if (overridden != null && overridden != originalModel) {
        ignoreNested = true;
        BakedModel finalModel = overridden.getOverrides().resolve(overridden, stack, world, entity, seed);
        ignoreNested = false;
        return finalModel;
      }
    }
    return originalModel;
  }
}

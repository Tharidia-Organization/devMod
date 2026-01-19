package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.BiFunction;

/** Minimal persistent data storage for tool models. */
public final class ModDataNBT implements IModDataView {
  private static final String TAG_PERSISTENT = "FoundryToolPersistent";

  public static final ModDataNBT EMPTY = new ModDataNBT(new CompoundTag());

  private final CompoundTag tag;

  public ModDataNBT(CompoundTag tag) {
    this.tag = tag;
  }

  public static ModDataNBT fromStack(ItemStack stack) {
    CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    CompoundTag root = data.copyTag();
    if (root.contains(TAG_PERSISTENT, Tag.TAG_COMPOUND)) {
      return new ModDataNBT(root.getCompound(TAG_PERSISTENT));
    }
    return EMPTY;
  }

  @Override
  public <T> T get(ResourceLocation name, BiFunction<CompoundTag, String, T> function) {
    return function.apply(tag, name.toString());
  }

  @Override
  public boolean contains(ResourceLocation name) {
    return tag.contains(name.toString());
  }

  @Override
  public boolean contains(ResourceLocation name, int type) {
    return tag.contains(name.toString(), type);
  }
}

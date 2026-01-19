package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.function.BiFunction;

/** Read-only view of tool persistent data. */
public interface IModDataView {
  IModDataView EMPTY = new IModDataView() {
    @Override
    public <T> T get(ResourceLocation name, BiFunction<CompoundTag, String, T> function) {
      return function.apply(new CompoundTag(), name.toString());
    }

    @Override
    public boolean contains(ResourceLocation name) {
      return false;
    }

    @Override
    public boolean contains(ResourceLocation name, int type) {
      return false;
    }
  };

  <T> T get(ResourceLocation name, BiFunction<CompoundTag, String, T> function);

  boolean contains(ResourceLocation name);

  boolean contains(ResourceLocation name, int type);

  default boolean contains(ModifierId name) {
    return contains(name.asResourceLocation());
  }

  default boolean contains(ModifierId name, int type) {
    return contains(name.asResourceLocation(), type);
  }

  default Tag get(ResourceLocation name) {
    return get(name, CompoundTag::get);
  }

  default int getInt(ResourceLocation name) {
    return get(name, CompoundTag::getInt);
  }

  default int getInt(ModifierId name) {
    return getInt(name.asResourceLocation());
  }

  default boolean getBoolean(ResourceLocation name) {
    return get(name, CompoundTag::getBoolean);
  }

  default float getFloat(ResourceLocation name) {
    return get(name, CompoundTag::getFloat);
  }

  default String getString(ResourceLocation name) {
    return get(name, CompoundTag::getString);
  }

  default String getString(ModifierId name) {
    return getString(name.asResourceLocation());
  }

  default CompoundTag getCompound(ResourceLocation name) {
    return get(name, CompoundTag::getCompound);
  }

  default CompoundTag getCompound(ModifierId name) {
    return getCompound(name.asResourceLocation());
  }
}

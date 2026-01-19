package slimeknights.tconstruct.library.tools.nbt;

import com.devmod.foundry.tool.FoundryToolData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Minimal list of material IDs for tool rendering. */
public final class MaterialIdNBT {
  public static final MaterialIdNBT EMPTY = new MaterialIdNBT(List.of());

  private final List<MaterialVariantId> materials;

  public MaterialIdNBT(List<MaterialVariantId> materials) {
    this.materials = List.copyOf(materials);
  }

  public List<MaterialVariantId> getMaterials() {
    return materials;
  }

  public MaterialVariantId getMaterial(int index) {
    if (index < 0 || index >= materials.size()) {
      return IMaterial.UNKNOWN_ID;
    }
    return materials.get(index);
  }

  public static MaterialIdNBT from(ItemStack stack) {
    return FoundryToolData.fromStack(stack)
      .map(data -> {
        List<MaterialVariantId> list = new ArrayList<>(data.materials().size());
        for (ResourceLocation id : data.materials()) {
          list.add(MaterialVariantId.create(id, ""));
        }
        return new MaterialIdNBT(list);
      })
      .orElse(EMPTY);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MaterialIdNBT other)) {
      return false;
    }
    return materials.equals(other.materials);
  }

  @Override
  public int hashCode() {
    return Objects.hash(materials);
  }
}

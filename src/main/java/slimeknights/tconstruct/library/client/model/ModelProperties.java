package slimeknights.tconstruct.library.client.model;

import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;

/** Model data properties used for TConstruct-style models. */
public final class ModelProperties {
  private ModelProperties() {}

  public static final ModelProperty<FluidStack> FLUID_STACK = new ModelProperty<>();
  public static final ModelProperty<Integer> TANK_CAPACITY = new ModelProperty<>();
  public static final ModelProperty<MaterialVariantId> MATERIAL = new ModelProperty<>();
  public static final ModelProperty<MaterialIdNBT> MATERIALS = new ModelProperty<>();
}

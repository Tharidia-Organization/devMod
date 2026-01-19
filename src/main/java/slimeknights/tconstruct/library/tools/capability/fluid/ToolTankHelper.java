package slimeknights.tconstruct.library.tools.capability.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** Minimal helper for tool fluid storage in model rendering. */
public class ToolTankHelper {
  public static final ToolTankHelper TANK_HELPER = new ToolTankHelper();

  public FluidStack getFluid(IToolStackView tool) {
    return FluidStack.EMPTY;
  }

  public int getCapacity(IToolStackView tool) {
    return 0;
  }
}

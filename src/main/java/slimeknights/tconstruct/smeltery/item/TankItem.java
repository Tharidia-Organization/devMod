package slimeknights.tconstruct.smeltery.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Optional;

/** Minimal tank item helper for model overrides. */
public final class TankItem {
  private TankItem() {}

  public static FluidTank getTank(ItemStack stack, int multiplier) {
    int capacity = FluidType.BUCKET_VOLUME * Math.max(1, multiplier);
    FluidTank tank = new FluidTank(capacity);
    Optional<FluidStack> contained = FluidUtil.getFluidContained(stack);
    contained.ifPresent(fluid -> tank.setFluid(fluid.copy()));
    return tank;
  }
}

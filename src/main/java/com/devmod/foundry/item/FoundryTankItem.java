package com.devmod.foundry.item;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Helper for reading fluid contents from tank-like items.
 */
public final class FoundryTankItem {
    private FoundryTankItem() {}

    public static FluidTank getTank(ItemStack stack, int defaultCapacity) {
        int capacity = Math.max(1, defaultCapacity);
        if (stack == null || stack.isEmpty()) {
            return new FluidTank(capacity);
        }

        Optional<IFluidHandlerItem> handlerOptional = FluidUtil.getFluidHandler(stack);
        if (handlerOptional.isPresent()) {
            IFluidHandlerItem handler = handlerOptional.get();
            if (handler.getTanks() > 0) {
                int handlerCapacity = handler.getTankCapacity(0);
                if (handlerCapacity > 0) {
                    capacity = handlerCapacity;
                }
                FluidTank tank = new FluidTank(capacity);
                FluidStack fluid = handler.getFluidInTank(0);
                if (!fluid.isEmpty()) {
                    tank.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
                }
                return tank;
            }
        }

        FluidTank tank = new FluidTank(capacity);
        FluidUtil.getFluidContained(stack).ifPresent(fluid ->
            tank.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE)
        );
        return tank;
    }
}

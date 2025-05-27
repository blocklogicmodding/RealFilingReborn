package com.blocklogic.realfilingreborn.inventory;

import com.blocklogic.realfilingreborn.util.ConnectedCabinets;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Virtual fluid handler that exposes all connected fluid cabinet tanks as one unified fluid system
 */
public class IndexFluidHandler implements IFluidHandler {

    private final ConnectedCabinets connectedCabinets;
    private FluidHandlerTankSelector[] selectors;
    private int totalTanks = 0;

    public IndexFluidHandler(ConnectedCabinets connectedCabinets) {
        this.connectedCabinets = connectedCabinets;
        invalidateTanks();
    }

    /**
     * Rebuilds the virtual tank mapping when network changes
     */
    public void invalidateTanks() {
        List<FluidHandlerTankSelector> selectorList = new ArrayList<>();
        this.totalTanks = 0;

        for (IFluidHandler handler : connectedCabinets.getFluidHandlers()) {
            // Skip recursive handlers to prevent infinite loops
            if (handler instanceof IndexFluidHandler) continue;

            int handlerTanks = handler.getTanks();
            for (int i = 0; i < handlerTanks; i++) {
                selectorList.add(new FluidHandlerTankSelector(handler, i));
            }
            this.totalTanks += handlerTanks;
        }

        this.selectors = selectorList.toArray(new FluidHandlerTankSelector[0]);
    }

    private FluidHandlerTankSelector getSelectorForTank(int tank) {
        return (tank >= 0 && tank < selectors.length) ? selectors[tank] : null;
    }

    @Override
    public int getTanks() {
        return totalTanks;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidHandlerTankSelector selector = getSelectorForTank(tank);
        return selector != null ? selector.getFluidInTank() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        FluidHandlerTankSelector selector = getSelectorForTank(tank);
        return selector != null ? selector.getTankCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        FluidHandlerTankSelector selector = getSelectorForTank(tank);
        return selector != null && selector.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;

        // Try to fill existing tanks with the same fluid first
        for (FluidHandlerTankSelector selector : selectors) {
            FluidStack tankFluid = selector.getFluidInTank();
            if (!tankFluid.isEmpty() && tankFluid.isFluidEqual(resource)) {
                int filled = selector.fill(resource, action);
                if (filled > 0) {
                    return filled;
                }
            }
        }

        // If no existing tanks can accept it, try empty tanks
        for (FluidHandlerTankSelector selector : selectors) {
            FluidStack tankFluid = selector.getFluidInTank();
            if (tankFluid.isEmpty()) {
                int filled = selector.fill(resource, action);
                if (filled > 0) {
                    return filled;
                }
            }
        }

        return 0;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;

        // Find tanks with matching fluid
        for (FluidHandlerTankSelector selector : selectors) {
            FluidStack tankFluid = selector.getFluidInTank();
            if (!tankFluid.isEmpty() && tankFluid.isFluidEqual(resource)) {
                FluidStack drained = selector.drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
        }

        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;

        // Drain from the first available tank
        for (FluidHandlerTankSelector selector : selectors) {
            FluidStack tankFluid = selector.getFluidInTank();
            if (!tankFluid.isEmpty()) {
                FluidStack drained = selector.drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
        }

        return FluidStack.EMPTY;
    }

    /**
     * Wrapper class that maps virtual tanks to actual handler+tank combinations
     */
    private static class FluidHandlerTankSelector {
        private final IFluidHandler handler;
        private final int tank;

        public FluidHandlerTankSelector(IFluidHandler handler, int tank) {
            this.handler = handler;
            this.tank = tank;
        }

        public FluidStack getFluidInTank() {
            return handler.getFluidInTank(tank);
        }

        public int getTankCapacity() {
            return handler.getTankCapacity(tank);
        }

        public boolean isFluidValid(@NotNull FluidStack stack) {
            return handler.isFluidValid(tank, stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            return handler.fill(resource, action);
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            return handler.drain(resource, action);
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            return handler.drain(maxDrain, action);
        }
    }
}
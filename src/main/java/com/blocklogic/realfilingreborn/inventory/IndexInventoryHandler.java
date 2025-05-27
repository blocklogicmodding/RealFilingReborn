package com.blocklogic.realfilingreborn.inventory;

import com.blocklogic.realfilingreborn.util.ConnectedCabinets;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Virtual inventory handler that exposes all connected cabinet slots as one unified inventory
 */
public class IndexInventoryHandler implements IItemHandler {

    private final ConnectedCabinets connectedCabinets;
    private HandlerSlotSelector[] selectors;
    private int totalSlots = 0;

    public IndexInventoryHandler(ConnectedCabinets connectedCabinets) {
        this.connectedCabinets = connectedCabinets;
        invalidateSlots();
    }

    /**
     * Rebuilds the virtual slot mapping when network changes
     */
    public void invalidateSlots() {
        List<HandlerSlotSelector> selectorList = new ArrayList<>();
        this.totalSlots = 0;

        for (IItemHandler handler : connectedCabinets.getItemHandlers()) {
            // Skip recursive handlers to prevent infinite loops
            if (handler instanceof IndexInventoryHandler) continue;

            int handlerSlots = handler.getSlots();
            for (int i = 0; i < handlerSlots; i++) {
                selectorList.add(new HandlerSlotSelector(handler, i));
            }
            this.totalSlots += handlerSlots;
        }

        this.selectors = selectorList.toArray(new HandlerSlotSelector[0]);
    }

    private HandlerSlotSelector getSelectorForSlot(int slot) {
        return (slot >= 0 && slot < selectors.length) ? selectors[slot] : null;
    }

    @Override
    public int getSlots() {
        return totalSlots;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        HandlerSlotSelector selector = getSelectorForSlot(slot);
        return selector != null ? selector.getStackInSlot() : ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        HandlerSlotSelector selector = getSelectorForSlot(slot);
        return selector != null ? selector.insertItem(stack, simulate) : stack;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        HandlerSlotSelector selector = getSelectorForSlot(slot);
        return selector != null ? selector.extractItem(amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        HandlerSlotSelector selector = getSelectorForSlot(slot);
        return selector != null ? selector.getSlotLimit() : 0;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        HandlerSlotSelector selector = getSelectorForSlot(slot);
        return selector != null && selector.isItemValid(stack);
    }

    /**
     * Wrapper class that maps virtual slots to actual handler+slot combinations
     */
    private static class HandlerSlotSelector {
        private final IItemHandler handler;
        private final int slot;

        public HandlerSlotSelector(IItemHandler handler, int slot) {
            this.handler = handler;
            this.slot = slot;
        }

        public ItemStack getStackInSlot() {
            return handler.getStackInSlot(slot);
        }

        public ItemStack insertItem(@NotNull ItemStack stack, boolean simulate) {
            return handler.insertItem(slot, stack, simulate);
        }

        public ItemStack extractItem(int amount, boolean simulate) {
            return handler.extractItem(slot, amount, simulate);
        }

        public int getSlotLimit() {
            return handler.getSlotLimit(slot);
        }

        public boolean isItemValid(@NotNull ItemStack stack) {
            return handler.isItemValid(slot, stack);
        }
    }
}
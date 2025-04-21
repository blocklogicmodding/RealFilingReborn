package com.blocklogic.realfilingreborn.capability;

import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CombinedItemHandler implements IItemHandler {
    private final FilingIndexBlockEntity indexEntity;

    public CombinedItemHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
    }

    private List<IItemHandler> getHandlers() {
        return indexEntity.getCabinetItemHandlers();
    }

    private int calculateTotalSlots() {
        return getHandlers().stream().mapToInt(IItemHandler::getSlots).sum();
    }

    private HandlerAndSlot resolveSlot(int slot) {
        List<IItemHandler> handlers = getHandlers();
        int currentIndex = 0;

        for (IItemHandler handler : handlers) {
            int handlerSlots = handler.getSlots();
            if (slot < currentIndex + handlerSlots) {
                return new HandlerAndSlot(handler, slot - currentIndex);
            }
            currentIndex += handlerSlots;
        }

        return null;
    }

    @Override
    public int getSlots() {
        return calculateTotalSlots();
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        HandlerAndSlot resolved = resolveSlot(slot);
        if (resolved != null) {
            return resolved.handler().getStackInSlot(resolved.slot());
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        HandlerAndSlot resolved = resolveSlot(slot);
        if (resolved != null) {
            return resolved.handler().insertItem(resolved.slot(), stack, simulate);
        }
        return stack;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        HandlerAndSlot resolved = resolveSlot(slot);
        if (resolved != null) {
            return resolved.handler().extractItem(resolved.slot(), amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        HandlerAndSlot resolved = resolveSlot(slot);
        if (resolved != null) {
            return resolved.handler().getSlotLimit(resolved.slot());
        }
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        HandlerAndSlot resolved = resolveSlot(slot);
        if (resolved != null) {
            return resolved.handler().isItemValid(resolved.slot(), stack);
        }
        return false;
    }

    private record HandlerAndSlot(IItemHandler handler, int slot) {}
}
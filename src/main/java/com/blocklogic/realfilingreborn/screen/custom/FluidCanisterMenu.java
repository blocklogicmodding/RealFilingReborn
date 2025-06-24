package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Optional;

public class FluidCanisterMenu extends AbstractContainerMenu {
    private final ItemStackHandler assignmentInventory;
    private final ItemStack canisterStack;
    private final int canisterSlot;
    private final Inventory playerInventory;

    public FluidCanisterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readInt());
    }

    public FluidCanisterMenu(int containerId, Inventory playerInventory, int canisterSlot) {
        super(ModMenuTypes.FLUID_CANISTER_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.canisterSlot = canisterSlot;
        this.canisterStack = playerInventory.getItem(canisterSlot);

        this.assignmentInventory = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                updateCanisterAssignment();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof BucketItem bucketItem &&
                        bucketItem.content != null &&
                        FluidHelper.isValidFluid(bucketItem.content);
            }
        };

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        this.addSlot(new SlotItemHandler(this.assignmentInventory, 0, 80, 43) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return assignmentInventory.isItemValid(0, stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                updateCanisterAssignment();
            }
        });
    }

    private void updateCanisterAssignment() {
        if (canisterStack.isEmpty()) return;

        ItemStack assignedBucket = assignmentInventory.getStackInSlot(0);

        if (assignedBucket.isEmpty()) {
            return;
        }

        if (!(assignedBucket.getItem() instanceof BucketItem bucketItem)) {
            return;
        }

        Fluid fluid = bucketItem.content;
        if (!FluidHelper.isValidFluid(fluid)) {
            return;
        }

        FluidCanisterItem.CanisterContents currentContents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

        if (currentContents == null || currentContents.storedFluidId().isEmpty()) {
            ResourceLocation fluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(fluid));

            FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                    Optional.of(fluidId), 1000);
            canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);

            assignmentInventory.setStackInSlot(0, new ItemStack(Items.BUCKET));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index == 36) {
                if (!this.moveItemStackTo(itemstack1, 0, 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (assignmentInventory.isItemValid(0, itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 36, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    public Component getCurrentCountText() {
        if (canisterStack.isEmpty()) return null;

        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
        if (contents != null && contents.storedFluidId().isPresent()) {
            int buckets = contents.amount() / 1000;
            int millibuckets = contents.amount() % 1000;
            String amountText = buckets > 0 ?
                    (millibuckets > 0 ? buckets + "." + (millibuckets / 100) + "B" : buckets + "B") :
                    millibuckets + "mB";
            return Component.translatable("gui.realfilingreborn.current_fluid_amount", amountText);
        }
        return null;
    }

    public void extractFluid() {
        if (canisterStack.isEmpty()) return;

        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
        if (contents == null || contents.storedFluidId().isEmpty() || contents.amount() < 1000) {
            return;
        }

        Player player = playerInventory.player;

        // Check if player has empty bucket
        boolean hasEmptyBucket = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.BUCKET) {
                hasEmptyBucket = true;
                break;
            }
        }

        if (!hasEmptyBucket) {
            return; // No empty bucket available
        }

        ResourceLocation fluidId = contents.storedFluidId().get();
        ItemStack bucketToGive = FluidHelper.getBucketForFluid(fluidId);

        if (bucketToGive.isEmpty()) {
            return; // No bucket available for this fluid type
        }

        // Remove empty bucket from inventory
        boolean bucketRemoved = false;
        for (int i = 0; i < player.getInventory().getContainerSize() && !bucketRemoved; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.BUCKET) {
                stack.shrink(1);
                bucketRemoved = true;
            }
        }

        if (!bucketRemoved) {
            return; // Couldn't remove bucket (shouldn't happen)
        }

        // Update canister contents
        int newAmount = contents.amount() - 1000;
        FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                contents.storedFluidId(),
                Math.max(0, newAmount)
        );
        canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);

        // Give filled bucket to player
        if (!player.getInventory().add(bucketToGive)) {
            // Inventory full, drop the bucket
            player.drop(bucketToGive, false);
        }

        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return !canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!assignmentInventory.getStackInSlot(0).isEmpty()) {
            player.drop(assignmentInventory.getStackInSlot(0), false);
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 70 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 128));
        }
    }
}
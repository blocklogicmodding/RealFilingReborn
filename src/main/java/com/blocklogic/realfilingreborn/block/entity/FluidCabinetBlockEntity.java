// Updated FluidCabinetBlockEntity - Keep bucket UX, hide ALL items from external storage
package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FluidCabinetBlock;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.screen.custom.FluidCabinetMenu;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FluidCabinetBlockEntity extends BlockEntity implements MenuProvider {

    public final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final Map<Direction, IItemHandler> handlers = new HashMap<>();
    private final Map<Direction, IFluidHandler> fluidHandlers = new HashMap<>();

    public FluidCabinetBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FLUID_CABINET_BE.get(), pos, blockState);
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        if (side != null && this.getBlockState().getValue(FluidCabinetBlock.FACING) == side) {
            return null;
        }

        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FluidCabinetItemHandler(this, s));
    }

    @Nullable
    public IFluidHandler getFluidCapabilityHandler(@Nullable Direction side) {
        if (side != null && this.getBlockState().getValue(FluidCabinetBlock.FACING) == side) {
            return null;
        }

        return fluidHandlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FluidCabinetFluidHandler(this, s));
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    // Update saveAdditional method to include controller position
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        // Save controller position
        if (controllerPos != null) {
            tag.putLong("controller_pos", controllerPos.asLong());
        }
    }

    // Update loadAdditional method to include controller position
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        // Load controller position
        if (tag.contains("controller_pos")) {
            this.controllerPos = BlockPos.of(tag.getLong("controller_pos"));
        } else {
            this.controllerPos = null;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("blockentity.realfilingreborn.fluid_cabinet_name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new FluidCabinetMenu(i, inventory, this);
    }

    @Override
    public void setRemoved() {
        // Notify controller that this cabinet is being removed BEFORE calling super
        if (controllerPos != null && level != null && !level.isClientSide()) {
            try {
                BlockEntity entity = level.getBlockEntity(controllerPos);
                if (entity instanceof FilingIndexBlockEntity filingIndex) {
                    filingIndex.getConnectedCabinets().removeCabinet(getBlockPos());
                }
            } catch (Exception e) {
                // Silently handle cleanup errors to prevent save hanging
            }
        }

        super.setRemoved();
    }

    private void notifyCanisterContentsChanged() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            setChanged();
        }
    }

    // FLUID HANDLER - Storage terminals see ONLY fluids
    private static class FluidCabinetFluidHandler implements IFluidHandler {
        private final FluidCabinetBlockEntity cabinet;
        private final Direction side;

        public FluidCabinetFluidHandler(FluidCabinetBlockEntity cabinet, @Nullable Direction side) {
            this.cabinet = cabinet;
            this.side = side;
        }

        @Override
        public int getTanks() {
            return 4;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0 || tank >= 4) return FluidStack.EMPTY;

            ItemStack canisterStack = cabinet.inventory.getStackInSlot(tank);
            if (canisterStack.getItem() instanceof FluidCanisterItem) {
                FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                if (contents != null && contents.storedFluidId().isPresent()) {
                    ResourceLocation fluidId = contents.storedFluidId().get();
                    Fluid fluid = FluidHelper.getFluidFromId(fluidId);
                    if (fluid != null) {
                        return new FluidStack(fluid, contents.amount());
                    }
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && FluidHelper.isValidFluid(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) return 0;

            ResourceLocation fluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(resource.getFluid()));

            for (int i = 0; i < 4; i++) {
                ItemStack canisterStack = cabinet.inventory.getStackInSlot(i);

                if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                    FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

                    if (contents != null) {
                        if (contents.storedFluidId().isEmpty() ||
                                FluidHelper.areFluidsCompatible(contents.storedFluidId().get(), fluidId)) {

                            int maxToAdd = Integer.MAX_VALUE - contents.amount();
                            int toAdd = Math.min(resource.getAmount(), maxToAdd);

                            if (toAdd > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        Optional.of(fluidId),
                                        contents.amount() + toAdd
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                cabinet.notifyCanisterContentsChanged();
                            }

                            return toAdd;
                        }
                    }
                }
            }

            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) return FluidStack.EMPTY;

            ResourceLocation fluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(resource.getFluid()));

            for (int i = 0; i < 4; i++) {
                ItemStack canisterStack = cabinet.inventory.getStackInSlot(i);

                if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                    FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

                    if (contents != null && contents.storedFluidId().isPresent() &&
                            FluidHelper.areFluidsCompatible(contents.storedFluidId().get(), fluidId)) {

                        int toDrain = Math.min(resource.getAmount(), contents.amount());

                        if (toDrain > 0 && action.execute()) {
                            FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                    contents.storedFluidId(),
                                    contents.amount() - toDrain
                            );
                            canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                            cabinet.notifyCanisterContentsChanged();
                        }

                        return new FluidStack(resource.getFluid(), toDrain);
                    }
                }
            }

            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < 4; i++) {
                ItemStack canisterStack = cabinet.inventory.getStackInSlot(i);

                if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                    FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

                    if (contents != null && contents.storedFluidId().isPresent() && contents.amount() > 0) {
                        ResourceLocation fluidId = contents.storedFluidId().get();
                        Fluid fluid = FluidHelper.getFluidFromId(fluidId);

                        if (fluid != null) {
                            int toDrain = Math.min(maxDrain, contents.amount());

                            if (toDrain > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        contents.storedFluidId(),
                                        contents.amount() - toDrain
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                cabinet.notifyCanisterContentsChanged();
                            }

                            return new FluidStack(fluid, toDrain);
                        }
                    }
                }
            }

            return FluidStack.EMPTY;
        }
    }

    // ITEM HANDLER - External storage sees NOTHING! Only players can interact
    private static class FluidCabinetItemHandler implements IItemHandler {
        private final FluidCabinetBlockEntity cabinet;
        private final Direction side;

        public FluidCabinetItemHandler(FluidCabinetBlockEntity cabinet, @Nullable Direction side) {
            this.cabinet = cabinet;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 0; // HIDE ALL SLOTS FROM EXTERNAL STORAGE
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY; // EXTERNAL STORAGE SEES NOTHING
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack; // REJECT ALL ITEM INSERTIONS FROM EXTERNAL STORAGE
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY; // REJECT ALL ITEM EXTRACTIONS FROM EXTERNAL STORAGE
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false; // NO ITEMS VALID FOR EXTERNAL STORAGE
        }
    }

    // Controller connection tracking
    private BlockPos controllerPos = null;

    /**
     * Sets the controller position when connected to a Filing Index
     */
    public void setControllerPos(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
        setChanged();
    }

    /**
     * Clears the controller position when disconnected
     */
    public void clearControllerPos() {
        this.controllerPos = null;
        setChanged();
    }

    /**
     * Gets the current controller position
     */
    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * Checks if this cabinet is connected to a controller
     */
    public boolean hasController() {
        return controllerPos != null;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }
}
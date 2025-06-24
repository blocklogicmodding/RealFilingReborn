package com.blocklogic.realfilingreborn.capability;

import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FilingIndexFluidHandler implements IFluidHandler {
    private final FilingIndexBlockEntity indexEntity;
    private final Level level;

    public FilingIndexFluidHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.level = indexEntity.getLevel();
    }

    private List<FluidTankInfo> getAllFluidTanks() {
        List<FluidTankInfo> tanks = new ArrayList<>();

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            // Check if cabinet is within range
            if (!isInRange(cabinetPos)) {
                continue;
            }

            // Handle Fluid Cabinets
            if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet && fluidCabinet.isLinkedToController()) {
                for (int slot = 0; slot < 4; slot++) {
                    ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(slot);

                    if (canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                        if (contents != null && contents.storedFluidId().isPresent() && contents.amount() > 0) {
                            ResourceLocation fluidId = contents.storedFluidId().get();
                            Fluid fluid = FluidHelper.getFluidFromId(fluidId);
                            if (fluid != null) {
                                tanks.add(new FluidTankInfo(cabinetPos, slot, new FluidStack(fluid, contents.amount())));
                            }
                        }
                    }
                }
            }
        }

        return tanks;
    }

    private boolean isInRange(BlockPos cabinetPos) {
        double distance = Math.sqrt(indexEntity.getBlockPos().distSqr(cabinetPos));
        return distance <= indexEntity.getRange();
    }

    @Override
    public int getTanks() {
        return getAllFluidTanks().size();
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        List<FluidTankInfo> tanks = getAllFluidTanks();
        if (tank < 0 || tank >= tanks.size()) {
            return FluidStack.EMPTY;
        }

        return tanks.get(tank).fluidStack.copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE; // Canisters have virtually unlimited capacity
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        List<FluidTankInfo> tanks = getAllFluidTanks();
        if (tank < 0 || tank >= tanks.size() || stack.isEmpty()) {
            return false;
        }

        FluidTankInfo tankInfo = tanks.get(tank);
        return FluidHelper.areFluidsCompatible(
                FluidHelper.getFluidId(stack.getFluid()),
                FluidHelper.getFluidId(tankInfo.fluidStack.getFluid())
        );
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) {
            return 0;
        }

        ResourceLocation resourceFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(resource.getFluid()));

        // Try to fill existing compatible tanks first
        List<FluidTankInfo> tanks = getAllFluidTanks();
        for (FluidTankInfo tankInfo : tanks) {
            ResourceLocation tankFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(tankInfo.fluidStack.getFluid()));

            if (FluidHelper.areFluidsCompatible(resourceFluidId, tankFluidId)) {
                if (level.getBlockEntity(tankInfo.cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(tankInfo.slotIndex);
                    if (canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                        if (contents != null) {
                            int maxToAdd = Integer.MAX_VALUE - contents.amount();
                            int toAdd = Math.min(resource.getAmount(), maxToAdd);

                            if (toAdd > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        Optional.of(resourceFluidId),
                                        contents.amount() + toAdd
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                fluidCabinet.setChanged();
                            }

                            return toAdd;
                        }
                    }
                }
            }
        }

        // Try to fill empty canisters
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) {
                continue;
            }

            if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet && fluidCabinet.isLinkedToController()) {
                for (int slot = 0; slot < 4; slot++) {
                    ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(slot);

                    if (canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                        if (contents != null && contents.storedFluidId().isEmpty()) {
                            int toAdd = Math.min(resource.getAmount(), Integer.MAX_VALUE);

                            if (toAdd > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        Optional.of(resourceFluidId),
                                        toAdd
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                fluidCabinet.setChanged();
                            }

                            return toAdd;
                        }
                    }
                }
            }
        }

        return 0; // No compatible tank found
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) {
            return FluidStack.EMPTY;
        }

        ResourceLocation resourceFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(resource.getFluid()));

        List<FluidTankInfo> tanks = getAllFluidTanks();
        for (FluidTankInfo tankInfo : tanks) {
            ResourceLocation tankFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(tankInfo.fluidStack.getFluid()));

            if (FluidHelper.areFluidsCompatible(resourceFluidId, tankFluidId)) {
                if (level.getBlockEntity(tankInfo.cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(tankInfo.slotIndex);
                    if (canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                        if (contents != null && contents.amount() > 0) {
                            int toDrain = Math.min(resource.getAmount(), contents.amount());

                            if (toDrain > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        contents.storedFluidId(),
                                        contents.amount() - toDrain
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                fluidCabinet.setChanged();
                            }

                            return new FluidStack(resource.getFluid(), toDrain);
                        }
                    }
                }
            }
        }

        return FluidStack.EMPTY;
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, FluidAction action) {
        List<FluidTankInfo> tanks = getAllFluidTanks();
        for (FluidTankInfo tankInfo : tanks) {
            if (tankInfo.fluidStack.getAmount() > 0) {
                if (level.getBlockEntity(tankInfo.cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(tankInfo.slotIndex);
                    if (canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                        if (contents != null && contents.amount() > 0) {
                            int toDrain = Math.min(maxDrain, contents.amount());

                            if (toDrain > 0 && action.execute()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        contents.storedFluidId(),
                                        contents.amount() - toDrain
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                fluidCabinet.setChanged();
                            }

                            return new FluidStack(tankInfo.fluidStack.getFluid(), toDrain);
                        }
                    }
                }
            }
        }

        return FluidStack.EMPTY;
    }

    private static class FluidTankInfo {
        final BlockPos cabinetPos;
        final int slotIndex;
        final FluidStack fluidStack;

        public FluidTankInfo(BlockPos cabinetPos, int slotIndex, FluidStack fluidStack) {
            this.cabinetPos = cabinetPos;
            this.slotIndex = slotIndex;
            this.fluidStack = fluidStack;
        }
    }
}
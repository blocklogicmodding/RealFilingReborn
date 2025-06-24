package com.blocklogic.realfilingreborn.capability;

import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

public class FilingIndexFluidHandler implements IFluidHandler {
    private final FilingIndexBlockEntity indexEntity;
    private final Level level;

    private volatile List<FluidTankInfo> cachedFluidTanks = null;
    private final AtomicLong lastCacheTime = new AtomicLong(0);
    private final AtomicLong cacheVersion = new AtomicLong(0);
    private static final long CACHE_DURATION_MS = 500;
    private static final int MAX_FLUID_TANKS_PER_SCAN = 500;

    private final Map<BlockPos, Boolean> inRangeCache = new ConcurrentHashMap<>();
    private volatile long lastRangeCacheTime = 0;
    private static final long RANGE_CACHE_DURATION_MS = 2000;

    public FilingIndexFluidHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.level = indexEntity.getLevel();
    }

    private void notifyUpdate(BlockPos cabinetPos) {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(cabinetPos, level.getBlockState(cabinetPos), level.getBlockState(cabinetPos), Block.UPDATE_CLIENTS);
            invalidateCache();
        }
    }

    public void invalidateCache() {
        cachedFluidTanks = null;
        cacheVersion.incrementAndGet();
        lastCacheTime.set(0);
    }

    private void invalidateRangeCache() {
        inRangeCache.clear();
        lastRangeCacheTime = 0;
    }

    private List<FluidTankInfo> getAllFluidTanks() {
        long currentTime = System.currentTimeMillis();
        long currentVersion = cacheVersion.get();

        List<FluidTankInfo> cached = cachedFluidTanks;
        if (cached != null && (currentTime - lastCacheTime.get()) < CACHE_DURATION_MS) {
            return cached;
        }

        List<FluidTankInfo> tanks = new ArrayList<>();
        int tankCount = 0;

        List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());

        for (BlockPos cabinetPos : cabinets) {
            if (tankCount >= MAX_FLUID_TANKS_PER_SCAN) {
                break;
            }

            if (!isInRangeCached(cabinetPos)) {
                continue;
            }

            try {
                if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet && fluidCabinet.isLinkedToController()) {
                    for (int slot = 0; slot < 4; slot++) {
                        if (tankCount >= MAX_FLUID_TANKS_PER_SCAN) break;

                        ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(slot);

                        if (canisterStack.getItem() instanceof FluidCanisterItem) {
                            FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                            if (contents != null && contents.storedFluidId().isPresent() && contents.amount() > 0) {
                                ResourceLocation fluidId = contents.storedFluidId().get();
                                Fluid fluid = FluidHelper.getFluidFromId(fluidId);
                                if (fluid != null) {
                                    tanks.add(new FluidTankInfo(cabinetPos, slot, new FluidStack(fluid, contents.amount())));
                                    tankCount++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }

        if (currentVersion == cacheVersion.get()) {
            cachedFluidTanks = tanks;
            lastCacheTime.set(currentTime);
        }

        return tanks;
    }

    private boolean isInRangeCached(BlockPos cabinetPos) {
        long currentTime = System.currentTimeMillis();

        if ((currentTime - lastRangeCacheTime) > RANGE_CACHE_DURATION_MS) {
            invalidateRangeCache();
            lastRangeCacheTime = currentTime;
        }

        return inRangeCache.computeIfAbsent(cabinetPos, pos -> {
            try {
                return indexEntity.isInRange(pos);
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public int getTanks() {
        try {
            return getAllFluidTanks().size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        try {
            List<FluidTankInfo> tanks = getAllFluidTanks();
            if (tank < 0 || tank >= tanks.size()) {
                return FluidStack.EMPTY;
            }

            return tanks.get(tank).fluidStack.copy();
        } catch (Exception e) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        try {
            List<FluidTankInfo> tanks = getAllFluidTanks();
            if (tank < 0 || tank >= tanks.size() || stack.isEmpty()) {
                return false;
            }

            FluidTankInfo tankInfo = tanks.get(tank);
            return FluidHelper.areFluidsCompatible(
                    FluidHelper.getFluidId(stack.getFluid()),
                    FluidHelper.getFluidId(tankInfo.fluidStack.getFluid())
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) {
            return 0;
        }

        try {
            ResourceLocation resourceFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(resource.getFluid()));

            List<FluidTankInfo> tanks = getAllFluidTanks();
            for (FluidTankInfo tankInfo : tanks) {
                ResourceLocation tankFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(tankInfo.fluidStack.getFluid()));

                if (FluidHelper.areFluidsCompatible(resourceFluidId, tankFluidId)) {
                    if (level.getBlockEntity(tankInfo.cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                        ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(tankInfo.slotIndex);
                        if (canisterStack.getItem() instanceof FluidCanisterItem) {
                            FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                            if (contents != null) {
                                if (contents.amount() > Integer.MAX_VALUE - resource.getAmount()) {
                                    return 0;
                                }

                                long newAmount = (long)contents.amount() + resource.getAmount();
                                int maxToAdd = newAmount > Integer.MAX_VALUE ?
                                        Integer.MAX_VALUE - contents.amount() : resource.getAmount();

                                if (maxToAdd > 0 && action.execute()) {
                                    FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                            Optional.of(resourceFluidId),
                                            contents.amount() + maxToAdd
                                    );
                                    canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);
                                    fluidCabinet.setChanged();
                                    notifyUpdate(tankInfo.cabinetPos);
                                }

                                return maxToAdd;
                            }
                        }
                    }
                }
            }

            List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());
            for (BlockPos cabinetPos : cabinets) {
                if (!isInRangeCached(cabinetPos)) {
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
                                    notifyUpdate(cabinetPos);
                                }

                                return toAdd;
                            }
                        }
                    }
                }
            }

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !FluidHelper.isValidFluid(resource.getFluid())) {
            return FluidStack.EMPTY;
        }

        try {
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
                                    notifyUpdate(tankInfo.cabinetPos);
                                }

                                return new FluidStack(resource.getFluid(), toDrain);
                            }
                        }
                    }
                }
            }

            return FluidStack.EMPTY;
        } catch (Exception e) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, FluidAction action) {
        try {
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
                                    notifyUpdate(tankInfo.cabinetPos);
                                }

                                return new FluidStack(tankInfo.fluidStack.getFluid(), toDrain);
                            }
                        }
                    }
                }
            }

            return FluidStack.EMPTY;
        } catch (Exception e) {
            return FluidStack.EMPTY;
        }
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
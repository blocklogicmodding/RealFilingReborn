package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.capability.FilingIndexFluidHandler;
import com.blocklogic.realfilingreborn.capability.FilingIndexItemHandler;
import com.blocklogic.realfilingreborn.item.custom.DiamondRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.IronRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.NetheriteRangeUpgrade;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    private final Set<BlockPos> linkedCabinets = new HashSet<>();
    private final Map<Direction, IItemHandler> handlers = new ConcurrentHashMap<>();
    private final Map<Direction, IFluidHandler> fluidHandlers = new ConcurrentHashMap<>();

    // PERFORMANCE: Cache for range calculations
    private final Map<BlockPos, Boolean> rangeCache = new ConcurrentHashMap<>();
    private int lastKnownRange = -1;
    private static final int MAX_HANDLER_CACHE_SIZE = 32;

    // PERFORMANCE: Debouncing for connected state updates
    private boolean updateScheduled = false;
    private boolean needsConnectedStateUpdate = false;

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            // PERFORMANCE: Clear range cache when upgrade changes
            clearRangeCache();
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    // PERFORMANCE: Optimized range checking with caching
    public boolean isInRange(BlockPos cabinetPos) {
        int currentRange = getRange();
        if (currentRange != lastKnownRange) {
            clearRangeCache();
            lastKnownRange = currentRange;
        }

        return rangeCache.computeIfAbsent(cabinetPos, pos -> {
            double distSq = getBlockPos().distSqr(pos);
            double rangeSq = (double) currentRange * currentRange;
            return distSq <= rangeSq; // No expensive sqrt needed
        });
    }

    private void clearRangeCache() {
        rangeCache.clear();
        lastKnownRange = -1;
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        // PERFORMANCE: Periodic cache cleanup to prevent memory leaks
        if (handlers.size() > MAX_HANDLER_CACHE_SIZE) {
            handlers.clear();
        }
        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingIndexItemHandler(this));
    }

    @Nullable
    public IFluidHandler getFluidCapabilityHandler(@Nullable Direction side) {
        // PERFORMANCE: Periodic cache cleanup to prevent memory leaks
        if (fluidHandlers.size() > MAX_HANDLER_CACHE_SIZE) {
            fluidHandlers.clear();
        }
        return fluidHandlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingIndexFluidHandler(this));
    }

    public void drops() {
        // Clear all linked cabinets first
        clearAllLinkedCabinets();

        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    public void addCabinet(BlockPos cabinetPos) {
        boolean wasEmpty = linkedCabinets.isEmpty();
        linkedCabinets.add(cabinetPos);
        clearRangeCache(); // PERFORMANCE: Clear cache when cabinets change
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            // PERFORMANCE: Schedule update instead of immediate execution
            if (wasEmpty) {
                scheduleConnectedStateUpdate();
            }
        }
    }

    public void removeCabinet(BlockPos cabinetPos) {
        boolean wasRemoved = linkedCabinets.remove(cabinetPos);
        if (wasRemoved) {
            clearRangeCache(); // PERFORMANCE: Clear cache when cabinets change
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                // PERFORMANCE: Schedule update instead of immediate execution
                if (linkedCabinets.isEmpty()) {
                    scheduleConnectedStateUpdate();
                }
            }
        }
    }

    // PERFORMANCE: Batch cabinet operations
    public void addCabinets(Set<BlockPos> cabinets) {
        if (cabinets.isEmpty()) return;

        boolean wasEmpty = linkedCabinets.isEmpty();
        linkedCabinets.addAll(cabinets);
        clearRangeCache();
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            if (wasEmpty && !linkedCabinets.isEmpty()) {
                scheduleConnectedStateUpdate();
            }
        }
    }

    public void removeCabinets(Set<BlockPos> cabinets) {
        if (cabinets.isEmpty()) return;

        boolean hadCabinets = !linkedCabinets.isEmpty();
        boolean changed = linkedCabinets.removeAll(cabinets);

        if (changed) {
            clearRangeCache();
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                if (hadCabinets && linkedCabinets.isEmpty()) {
                    scheduleConnectedStateUpdate();
                }
            }
        }
    }

    // PERFORMANCE: Debounced connected state updates
    private void scheduleConnectedStateUpdate() {
        if (!updateScheduled && level != null && !level.isClientSide()) {
            updateScheduled = true;
            needsConnectedStateUpdate = true;
            // Schedule for next tick to batch multiple changes
            level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
        }
    }

    // Called by the block's tick method
    public void performScheduledUpdate() {
        if (updateScheduled && needsConnectedStateUpdate) {
            updateScheduled = false;
            needsConnectedStateUpdate = false;
            updateConnectedStateImmediate();
        }
    }

    public Set<BlockPos> getLinkedCabinets() {
        return new HashSet<>(linkedCabinets);
    }

    public int getLinkedCabinetCount() {
        return linkedCabinets.size();
    }

    public boolean removeCabinetAt(BlockPos cabinetPos) {
        if (linkedCabinets.remove(cabinetPos)) {
            clearRangeCache(); // PERFORMANCE: Clear cache
            if (level != null && !level.isClientSide()) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    cabinet.clearControllerPos();
                } else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    fluidCabinet.clearControllerPos();
                }
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                // PERFORMANCE: Schedule instead of immediate update
                if (linkedCabinets.isEmpty()) {
                    scheduleConnectedStateUpdate();
                }
            }
            setChanged();
            return true;
        }
        return false;
    }

    public void clearAllLinkedCabinets() {
        if (level != null && !level.isClientSide()) {
            for (BlockPos cabinetPos : linkedCabinets) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    cabinet.clearControllerPos();
                } else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    fluidCabinet.clearControllerPos();
                }
            }
        }
        boolean hadCabinets = !linkedCabinets.isEmpty();
        linkedCabinets.clear();
        clearRangeCache(); // PERFORMANCE: Clear cache
        setChanged();

        // PERFORMANCE: Schedule update only if state actually changed
        if (hadCabinets && level != null && !level.isClientSide()) {
            scheduleConnectedStateUpdate();
        }
    }

    public int getRange() {
        ItemStack upgradeStack = inventory.getStackInSlot(0);
        if (upgradeStack.isEmpty()) {
            return 8; // Base range
        }

        if (upgradeStack.getItem() instanceof NetheriteRangeUpgrade) {
            return 64;
        } else if (upgradeStack.getItem() instanceof DiamondRangeUpgrade) {
            return 32;
        } else if (upgradeStack.getItem() instanceof IronRangeUpgrade) {
            return 16;
        }

        return 8; // Fallback
    }

    // PERFORMANCE: Renamed for clarity, now only called when actually needed
    public void updateConnectedState() {
        scheduleConnectedStateUpdate();
    }

    private void updateConnectedStateImmediate() {
        if (level == null || level.isClientSide()) return;

        BlockState currentState = getBlockState();
        if (!(currentState.getBlock() instanceof FilingIndexBlock)) return;

        boolean hasConnections = linkedCabinets.size() > 0;
        boolean currentlyConnected = currentState.getValue(FilingIndexBlock.CONNECTED);

        if (hasConnections != currentlyConnected) {
            BlockState newState = currentState.setValue(FilingIndexBlock.CONNECTED, hasConnections);
            level.setBlock(getBlockPos(), newState, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        // Save linked cabinets
        ListTag cabinetList = new ListTag();
        for (BlockPos cabinetPos : linkedCabinets) {
            cabinetList.add(LongTag.valueOf(cabinetPos.asLong()));
        }
        tag.put("linkedCabinets", cabinetList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        // Load linked cabinets
        linkedCabinets.clear();
        clearRangeCache(); // PERFORMANCE: Clear cache on load
        if (tag.contains("linkedCabinets")) {
            ListTag cabinetList = tag.getList("linkedCabinets", 4);
            for (int i = 0; i < cabinetList.size(); i++) {
                long posLong = ((LongTag) cabinetList.get(i)).getAsLong();
                BlockPos cabinetPos = BlockPos.of(posLong);
                linkedCabinets.add(cabinetPos);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("blockentity.realfilingreborn.filing_index_name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new FilingIndexMenu(i, inventory, this);
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
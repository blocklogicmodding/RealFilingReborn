package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.inventory.IndexFluidHandler;
import com.blocklogic.realfilingreborn.inventory.IndexInventoryHandler;
import com.blocklogic.realfilingreborn.item.custom.IndexRangerUpgradeDiamond;
import com.blocklogic.realfilingreborn.item.custom.IndexRangerUpgradeGold;
import com.blocklogic.realfilingreborn.item.custom.IndexRangerUpgradeNetherite;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import com.blocklogic.realfilingreborn.util.ConnectedCabinets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {

    // Inventory for upgrade item (1 slot)
    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                // Schedule rebuild for next tick to prevent infinite loops
                level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
            }
        }
    };

    // Connected cabinet network management
    private ConnectedCabinets connectedCabinets;
    private IndexInventoryHandler inventoryHandler;
    private IndexFluidHandler fluidHandler;
    private final Map<Direction, IItemHandler> itemHandlers = new HashMap<>();
    private final Map<Direction, IFluidHandler> fluidHandlers = new HashMap<>();
    private boolean isRebuilding = false; // Prevent rebuild loops
    private boolean isRemoving = false; // Prevent removal loops
    private boolean needsPostLoadRebuild = false; // NEW: Track if we need rebuild after world loads

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
        this.connectedCabinets = new ConnectedCabinets(null, this);
        this.inventoryHandler = new IndexInventoryHandler(connectedCabinets);
        this.fluidHandler = new IndexFluidHandler(connectedCabinets);

        // Set virtual handler references for invalidation
        this.connectedCabinets.setVirtualHandlers(inventoryHandler, fluidHandler);
    }

    /**
     * Gets the item handler capability for external access
     */
    @Nullable
    public IItemHandler getItemCapabilityHandler(@Nullable Direction side) {
        return itemHandlers.computeIfAbsent(side != null ? side : Direction.UP, s -> inventoryHandler);
    }

    /**
     * Gets the fluid handler capability for external access
     */
    @Nullable
    public IFluidHandler getFluidCapabilityHandler(@Nullable Direction side) {
        return fluidHandlers.computeIfAbsent(side != null ? side : Direction.UP, s -> fluidHandler);
    }

    /**
     * Gets the current connection range based on installed upgrade
     */
    public int getConnectionRange() {
        ItemStack upgradeStack = inventory.getStackInSlot(0);
        if (upgradeStack.isEmpty()) {
            return 8; // Base range
        }

        if (upgradeStack.getItem() instanceof IndexRangerUpgradeGold) {
            return 16;
        } else if (upgradeStack.getItem() instanceof IndexRangerUpgradeDiamond) {
            return 32;
        } else if (upgradeStack.getItem() instanceof IndexRangerUpgradeNetherite) {
            return 64;
        }

        return 8; // Fallback to base range
    }

    /**
     * Adds a single cabinet to the network (called by LedgerItem)
     */
    public boolean addConnectedCabinet(LedgerItem.ActionMode mode, BlockPos cabinetPos) {
        boolean success;
        if (mode == LedgerItem.ActionMode.ADD) {
            success = connectedCabinets.addCabinet(cabinetPos);
        } else {
            success = connectedCabinets.removeCabinet(cabinetPos);
        }

        if (success) {
            updateConnectedState();
            // Schedule rebuild for next tick instead of immediate
            if (level != null && !level.isClientSide()) {
                level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
            }
        }

        return success;
    }

    /**
     * Adds multiple cabinets to the network (called by LedgerItem for area selection)
     */
    public boolean addConnectedCabinets(LedgerItem.ActionMode mode, BlockPos... cabinetPositions) {
        boolean anyChanged = false;

        for (BlockPos cabinetPos : cabinetPositions) {
            if (level != null) {
                BlockEntity entity = level.getBlockEntity(cabinetPos);
                if (entity instanceof FilingCabinetBlockEntity || entity instanceof FluidCabinetBlockEntity) {
                    if (mode == LedgerItem.ActionMode.ADD) {
                        if (connectedCabinets.addCabinet(cabinetPos)) {
                            anyChanged = true;
                        }
                    } else {
                        if (connectedCabinets.removeCabinet(cabinetPos)) {
                            anyChanged = true;
                        }
                    }
                }
            }
        }

        if (anyChanged) {
            updateConnectedState();
            // Schedule rebuild for next tick instead of immediate
            if (level != null && !level.isClientSide()) {
                level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
            }
        }

        return anyChanged;
    }

    /**
     * Rebuilds the network - removes invalid connections and updates state
     * FIXED: Prevent save hanging by making this async-safe
     */
    public void rebuildNetwork() {
        if (level == null || level.isClientSide() || isRebuilding) return;

        isRebuilding = true;
        try {
            connectedCabinets.setLevel(level);

            // FIXED: Don't call full rebuild during world save
            // Instead, just validate existing connections
            List<Long> toRemove = new ArrayList<>();
            for (Long cabinetLong : connectedCabinets.getConnectedCabinets()) {
                BlockPos cabinetPos = BlockPos.of(cabinetLong);
                BlockEntity entity = level.getBlockEntity(cabinetPos);

                // Check if cabinet still exists and is in range
                if (!(entity instanceof FilingCabinetBlockEntity) && !(entity instanceof FluidCabinetBlockEntity)) {
                    toRemove.add(cabinetLong);
                } else {
                    int range = getConnectionRange();
                    if (getBlockPos().distSqr(cabinetPos) > (range * range)) {
                        toRemove.add(cabinetLong);
                    }
                }
            }

            // Remove invalid connections
            for (Long cabinetLong : toRemove) {
                connectedCabinets.getConnectedCabinets().remove(cabinetLong);
            }

            // FIXED: After world load, restore controller references
            if (needsPostLoadRebuild) {
                restoreControllerReferences();
                needsPostLoadRebuild = false;
            }

            // Light rebuild - just refresh handlers
            connectedCabinets.lightRebuild();
            updateConnectedState();
            setChanged();
        } finally {
            isRebuilding = false;
        }
    }

    /**
     * NEW: Restores controller references after world load
     */
    private void restoreControllerReferences() {
        if (level == null || level.isClientSide()) return;

        for (Long cabinetLong : connectedCabinets.getConnectedCabinets()) {
            BlockPos cabinetPos = BlockPos.of(cabinetLong);
            BlockEntity entity = level.getBlockEntity(cabinetPos);

            if (entity instanceof FilingCabinetBlockEntity filingCabinet) {
                // Only set controller if it doesn't already have one or it's pointing to us
                BlockPos currentController = filingCabinet.getControllerPos();
                if (currentController == null || currentController.equals(getBlockPos())) {
                    filingCabinet.setControllerPos(getBlockPos());
                }
            } else if (entity instanceof FluidCabinetBlockEntity fluidCabinet) {
                // Only set controller if it doesn't already have one or it's pointing to us
                BlockPos currentController = fluidCabinet.getControllerPos();
                if (currentController == null || currentController.equals(getBlockPos())) {
                    fluidCabinet.setControllerPos(getBlockPos());
                }
            }
        }
    }

    /**
     * Updates the CONNECTED blockstate based on whether we have connections
     */
    private void updateConnectedState() {
        if (level != null && !level.isClientSide()) {
            boolean hasConnections = connectedCabinets.hasConnections();
            if (getBlockState().getBlock() instanceof FilingIndexBlock indexBlock) {
                indexBlock.updateConnectedState(level, getBlockPos(), hasConnections);
            }
        }
    }

    /**
     * Gets the connected cabinets utility
     */
    public ConnectedCabinets getConnectedCabinets() {
        return connectedCabinets;
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }
        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("connected_cabinets", connectedCabinets.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        connectedCabinets.deserializeNBT(registries, tag.getCompound("connected_cabinets"));

        // FIXED: Don't schedule immediate rebuild - wait for world to fully load
        needsPostLoadRebuild = true;
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

    @Override
    public void setRemoved() {
        // FIXED: Prevent infinite loops and world save hanging
        if (isRemoving) {
            super.setRemoved();
            return;
        }

        isRemoving = true;

        // Clear all connections WITHOUT calling setRemoved on cabinets
        if (level != null && !level.isClientSide() && connectedCabinets != null) {
            try {
                // Just clear the controller references without triggering rebuilds
                for (Long cabinetLong : new ArrayList<>(connectedCabinets.getConnectedCabinets())) {
                    BlockPos cabinetPos = BlockPos.of(cabinetLong);
                    BlockEntity entity = level.getBlockEntity(cabinetPos);
                    if (entity instanceof FilingCabinetBlockEntity filingCabinet) {
                        filingCabinet.clearControllerPos();
                    } else if (entity instanceof FluidCabinetBlockEntity fluidCabinet) {
                        fluidCabinet.clearControllerPos();
                    }
                }

                // Clear the list directly
                connectedCabinets.getConnectedCabinets().clear();
            } catch (Exception e) {
                // Silently handle cleanup errors
            }
        }

        super.setRemoved();
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            // FIXED: Schedule rebuild after world fully loads (longer delay)
            level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 40); // 2 second delay
        }
    }
}
package com.blocklogic.realfilingreborn.util;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.inventory.IndexFluidHandler;
import com.blocklogic.realfilingreborn.inventory.IndexInventoryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConnectedCabinets implements INBTSerializable<CompoundTag> {

    private final FilingIndexBlockEntity indexEntity;

    private List<Long> connectedCabinets;
    private List<IItemHandler> itemHandlers;
    private List<IFluidHandler> fluidHandlers;
    private Level level;

    // References to virtual handlers for invalidation
    private IndexInventoryHandler inventoryHandler;
    private IndexFluidHandler fluidHandler;
    private boolean isRebuilding = false; // Prevent rebuild loops

    public ConnectedCabinets(Level level, FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.connectedCabinets = new ArrayList<>();
        this.itemHandlers = new ArrayList<>();
        this.fluidHandlers = new ArrayList<>();
        this.level = level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    /**
     * Sets references to the virtual handlers for invalidation
     */
    public void setVirtualHandlers(IndexInventoryHandler inventoryHandler, IndexFluidHandler fluidHandler) {
        this.inventoryHandler = inventoryHandler;
        this.fluidHandler = fluidHandler;
    }

    /**
     * FIXED: Light rebuild that doesn't cause save hanging
     * Only refreshes handler lists without expensive validation
     */
    public void lightRebuild() {
        if (isRebuilding) return;

        isRebuilding = true;
        try {
            this.itemHandlers = new ArrayList<>();
            this.fluidHandlers = new ArrayList<>();

            if (level != null && !level.isClientSide()) {
                // Build handler lists from existing valid cabinets (no validation)
                for (Long cabinetLong : this.connectedCabinets) {
                    BlockPos pos = BlockPos.of(cabinetLong);
                    BlockEntity entity = level.getBlockEntity(pos);

                    if (entity instanceof FilingCabinetBlockEntity filingCabinet) {
                        IItemHandler handler = filingCabinet.getCapabilityHandler(null);
                        if (handler != null) {
                            this.itemHandlers.add(handler);
                        }

                    } else if (entity instanceof FluidCabinetBlockEntity fluidCabinet) {
                        IItemHandler itemHandler = fluidCabinet.getCapabilityHandler(null);
                        if (itemHandler != null) {
                            this.itemHandlers.add(itemHandler);
                        }

                        IFluidHandler fluidHandler = fluidCabinet.getFluidCapabilityHandler(null);
                        if (fluidHandler != null) {
                            this.fluidHandlers.add(fluidHandler);
                        }
                    }
                }
            }

            // Invalidate any virtual inventory handlers that depend on this
            invalidateHandlers();
        } finally {
            isRebuilding = false;
        }
    }

    /**
     * Full rebuild - removes invalid connections and updates state
     * FIXED: Made safer to prevent save hanging
     */
    public void rebuild() {
        if (isRebuilding) return;

        isRebuilding = true;
        try {
            this.itemHandlers = new ArrayList<>();
            this.fluidHandlers = new ArrayList<>();

            if (level != null && !level.isClientSide()) {
                int range = indexEntity.getConnectionRange();
                AABB area = new AABB(indexEntity.getBlockPos()).inflate(range);

                // FIXED: Use iterator to safely remove items during iteration
                this.connectedCabinets.removeIf(cabinetLong -> {
                    BlockPos cabinetPos = BlockPos.of(cabinetLong);

                    // Check if out of range
                    if (!area.contains(Vec3.atCenterOf(cabinetPos))) {
                        return true;
                    }

                    // Check if cabinet still exists
                    try {
                        BlockEntity entity = level.getBlockEntity(cabinetPos);
                        return !(entity instanceof FilingCabinetBlockEntity) &&
                                !(entity instanceof FluidCabinetBlockEntity);
                    } catch (Exception e) {
                        // If we can't check, assume it's invalid
                        return true;
                    }
                });

                // Sort by distance (closer cabinets first for better performance)
                this.connectedCabinets.sort(Comparator.comparingDouble(value ->
                        BlockPos.of(value).distSqr(indexEntity.getBlockPos())));

                // Build handler lists from valid cabinets
                for (Long cabinetLong : this.connectedCabinets) {
                    BlockPos pos = BlockPos.of(cabinetLong);
                    try {
                        BlockEntity entity = level.getBlockEntity(pos);

                        if (entity instanceof FilingCabinetBlockEntity filingCabinet) {
                            // Get the cabinet's item handler (the one that handles virtual slots)
                            IItemHandler handler = filingCabinet.getCapabilityHandler(null);
                            if (handler != null) {
                                this.itemHandlers.add(handler);
                            }

                        } else if (entity instanceof FluidCabinetBlockEntity fluidCabinet) {
                            // Get both item and fluid handlers from fluid cabinets
                            IItemHandler itemHandler = fluidCabinet.getCapabilityHandler(null);
                            if (itemHandler != null) {
                                this.itemHandlers.add(itemHandler);
                            }

                            IFluidHandler fluidHandler = fluidCabinet.getFluidCapabilityHandler(null);
                            if (fluidHandler != null) {
                                this.fluidHandlers.add(fluidHandler);
                            }
                        }
                    } catch (Exception e) {
                        // Skip problematic cabinets silently
                    }
                }
            }

            // Invalidate any virtual inventory handlers that depend on this
            invalidateHandlers();
        } finally {
            isRebuilding = false;
        }
    }

    /**
     * Adds a cabinet to the network
     * FIXED: No immediate rebuild to prevent save hanging
     */
    public boolean addCabinet(BlockPos cabinetPos) {
        long cabinetLong = cabinetPos.asLong();
        if (!connectedCabinets.contains(cabinetLong)) {
            // Validate it's within range and is a valid cabinet
            if (level != null) {
                int range = indexEntity.getConnectionRange();
                if (indexEntity.getBlockPos().distSqr(cabinetPos) <= (range * range)) {
                    try {
                        BlockEntity entity = level.getBlockEntity(cabinetPos);
                        if (entity instanceof FilingCabinetBlockEntity || entity instanceof FluidCabinetBlockEntity) {
                            connectedCabinets.add(cabinetLong);
                            // Use light rebuild instead of full rebuild
                            lightRebuild();
                            return true;
                        }
                    } catch (Exception e) {
                        // Silently fail if cabinet can't be validated
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Removes a cabinet from the network
     * FIXED: No immediate rebuild to prevent save hanging
     */
    public boolean removeCabinet(BlockPos cabinetPos) {
        long cabinetLong = cabinetPos.asLong();
        boolean removed = connectedCabinets.remove(cabinetLong);
        if (removed) {
            // Use light rebuild instead of full rebuild
            lightRebuild();
        }
        return removed;
    }

    /**
     * Called to invalidate any virtual handlers that depend on this network
     */
    private void invalidateHandlers() {
        try {
            if (inventoryHandler != null) {
                inventoryHandler.invalidateSlots();
            }
            if (fluidHandler != null) {
                fluidHandler.invalidateTanks();
            }
        } catch (Exception e) {
            // Silently handle invalidation errors
        }
    }

    /**
     * Gets all item handlers in the network
     */
    public List<IItemHandler> getItemHandlers() {
        return new ArrayList<>(itemHandlers);
    }

    /**
     * Gets all fluid handlers in the network
     */
    public List<IFluidHandler> getFluidHandlers() {
        return new ArrayList<>(fluidHandlers);
    }

    /**
     * Gets the list of connected cabinet positions
     * FIXED: Return the actual list for direct manipulation during cleanup
     */
    public List<Long> getConnectedCabinets() {
        return connectedCabinets; // Return actual list, not copy
    }

    /**
     * Gets the count of connected cabinets
     */
    public int getCabinetCount() {
        return connectedCabinets.size();
    }

    /**
     * Checks if the network has any connections
     */
    public boolean hasConnections() {
        return !connectedCabinets.isEmpty();
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag compoundTag = new CompoundTag();

        // FIXED: Better serialization with size tracking
        compoundTag.putInt("size", this.connectedCabinets.size());
        for (int i = 0; i < this.connectedCabinets.size(); i++) {
            compoundTag.putLong("cabinet_" + i, this.connectedCabinets.get(i));
        }

        return compoundTag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag nbt) {
        this.connectedCabinets = new ArrayList<>();

        // FIXED: Better deserialization with size tracking
        if (nbt.contains("size")) {
            int size = nbt.getInt("size");
            for (int i = 0; i < size; i++) {
                String key = "cabinet_" + i;
                if (nbt.contains(key)) {
                    connectedCabinets.add(nbt.getLong(key));
                }
            }
        } else {
            // Fallback for old format
            for (String key : nbt.getAllKeys()) {
                if (key.startsWith("cabinet_")) {
                    connectedCabinets.add(nbt.getLong(key));
                } else if (!key.equals("size")) {
                    // Old numeric keys
                    try {
                        connectedCabinets.add(nbt.getLong(key));
                    } catch (NumberFormatException e) {
                        // Skip invalid keys
                    }
                }
            }
        }

        // FIXED: DON'T rebuild immediately after deserialization
        // Let the Filing Index handle restoration after world loads
    }
}
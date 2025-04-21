package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilingIndexBlockEntity extends BlockEntity {
    // Track connected cabinets by their positions
    private final Set<BlockPos> connectedCabinets = new HashSet<>();

    // Cache for cabinet item handlers
    private final List<IItemHandler> cabinetItemHandlers = new ArrayList<>();
    private boolean needsRefresh = true;

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    /**
     * Adds a cabinet to the network
     * @param cabinetPos Position of the cabinet to add
     * @return true if the cabinet was added, false if it was already in the network
     */
    public boolean addCabinet(BlockPos cabinetPos) {
        boolean added = connectedCabinets.add(cabinetPos);
        if (added) {
            needsRefresh = true;
            setChanged();
        }
        return added;
    }

    /**
     * Removes a cabinet from the network
     * @param cabinetPos Position of the cabinet to remove
     * @return true if the cabinet was removed, false if it wasn't in the network
     */
    public boolean removeCabinet(BlockPos cabinetPos) {
        boolean removed = connectedCabinets.remove(cabinetPos);
        if (removed) {
            needsRefresh = true;
            setChanged();
        }
        return removed;
    }

    /**
     * Checks if a cabinet is in the network
     * @param cabinetPos Position of the cabinet to check
     * @return true if the cabinet is in the network
     */
    public boolean hasCabinet(BlockPos cabinetPos) {
        return connectedCabinets.contains(cabinetPos);
    }

    /**
     * Gets the number of connected cabinets
     * @return Count of connected cabinets
     */
    public int getCabinetCount() {
        return connectedCabinets.size();
    }

    /**
     * Gets a list of all connected cabinet positions
     * @return Unmodifiable set of cabinet positions
     */
    public Set<BlockPos> getConnectedCabinets() {
        return Set.copyOf(connectedCabinets);
    }

    /**
     * Refreshes the list of cabinet item handlers
     */
    public void refreshCabinetHandlers() {
        cabinetItemHandlers.clear();
        Level level = this.getLevel();

        if (level != null) {
            // Filter out invalid cabinets and build handlers list
            Set<BlockPos> validCabinets = new HashSet<>();

            for (BlockPos cabinetPos : connectedCabinets) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinetEntity) {
                    validCabinets.add(cabinetPos);
                    IItemHandler handler = cabinetEntity.getCapabilityHandler(null);
                    if (handler != null) {
                        cabinetItemHandlers.add(handler);
                    }
                }
            }

            // Update connected cabinets if any were removed
            if (validCabinets.size() < connectedCabinets.size()) {
                connectedCabinets.retainAll(validCabinets);
                setChanged();
            }
        }

        needsRefresh = false;
    }

    /**
     * Gets the list of all cabinet item handlers
     * @return List of item handlers
     */
    public List<IItemHandler> getCabinetItemHandlers() {
        if (needsRefresh) {
            refreshCabinetHandlers();
        }
        return cabinetItemHandlers;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Save connected cabinet positions
        ListTag cabinetsList = new ListTag();
        for (BlockPos pos : connectedCabinets) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            cabinetsList.add(posTag);
        }
        tag.put("ConnectedCabinets", cabinetsList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load connected cabinet positions
        connectedCabinets.clear();
        if (tag.contains("ConnectedCabinets", Tag.TAG_LIST)) {
            ListTag cabinetsList = tag.getList("ConnectedCabinets", Tag.TAG_COMPOUND);
            for (int i = 0; i < cabinetsList.size(); i++) {
                CompoundTag posTag = cabinetsList.getCompound(i);
                // Now using the correct signature with both the tag and a key
                BlockPos pos = new BlockPos(
                        posTag.getInt("X"),
                        posTag.getInt("Y"),
                        posTag.getInt("Z")
                );
                connectedCabinets.add(pos);
            }
        }

        needsRefresh = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
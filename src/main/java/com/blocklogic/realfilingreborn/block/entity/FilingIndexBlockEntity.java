package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.custom.FluidCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.IndexCableCoreBlock;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    private static final int MAX_NETWORK_RANGE = 8;
    private UUID networkId;
    private Set<BlockPos> connectedCabinets = new HashSet<>();
    private Set<BlockPos> connectedCables = new HashSet<>();
    private boolean networkDirty = true;
    private final NetworkItemHandler networkItemHandler = new NetworkItemHandler();

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
        this.networkId = UUID.randomUUID();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FilingIndexBlockEntity blockEntity) {
        if (!level.isClientSide() && blockEntity.networkDirty) {
            blockEntity.refreshNetwork();
            blockEntity.networkDirty = false;
        }
    }

    public void markNetworkDirty() {
        this.networkDirty = true;
        setChanged();
    }

    private void refreshNetwork() {
        Set<BlockPos> oldCabinets = new HashSet<>(connectedCabinets);
        Set<BlockPos> oldCables = new HashSet<>(connectedCables);

        connectedCabinets.clear();
        connectedCables.clear();

        // Perform flood-fill search for connected devices
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new LinkedList<>();
        toVisit.add(this.worldPosition);
        visited.add(this.worldPosition);

        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.poll();

            // Check if we're within range
            if (current.distManhattan(this.worldPosition) > MAX_NETWORK_RANGE) {
                continue;
            }

            // Check all 6 directions
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);

                if (visited.contains(neighbor)) {
                    continue;
                }

                if (neighbor.distManhattan(this.worldPosition) > MAX_NETWORK_RANGE) {
                    continue;
                }

                BlockState neighborState = level.getBlockState(neighbor);
                Block neighborBlock = neighborState.getBlock();

                if (neighborBlock instanceof FilingCabinetBlock || neighborBlock instanceof FluidCabinetBlock) {
                    connectedCabinets.add(neighbor);
                    visited.add(neighbor);
                    // Cabinets don't propagate the network further
                } else if (neighborBlock instanceof IndexCableCoreBlock) {
                    connectedCables.add(neighbor);
                    visited.add(neighbor);
                    toVisit.add(neighbor); // Cables propagate the network
                } else if (neighborBlock instanceof FilingIndexBlock && !neighbor.equals(this.worldPosition)) {
                    // Found another Filing Index - this shouldn't happen in a well-designed network
                    // but we'll handle it gracefully by ignoring it
                    visited.add(neighbor);
                }
            }
        }

        // Check if network changed
        if (!oldCabinets.equals(connectedCabinets) || !oldCables.equals(connectedCables)) {
            // Network topology changed, invalidate cache and update clients
            networkItemHandler.invalidateCache();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public Set<BlockPos> getConnectedCabinets() {
        return new HashSet<>(connectedCabinets);
    }

    public Set<BlockPos> getConnectedCables() {
        return new HashSet<>(connectedCables);
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public IItemHandler getNetworkItemHandler() {
        return networkItemHandler;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("NetworkId", networkId);

        // Save connected positions for debugging/persistence
        CompoundTag cabinetsTag = new CompoundTag();
        int i = 0;
        for (BlockPos pos : connectedCabinets) {
            cabinetsTag.putLong("Cabinet" + i, pos.asLong());
            i++;
        }
        cabinetsTag.putInt("CabinetCount", i);
        tag.put("ConnectedCabinets", cabinetsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.hasUUID("NetworkId")) {
            this.networkId = tag.getUUID("NetworkId");
        } else {
            this.networkId = UUID.randomUUID();
        }

        // Load connected positions
        if (tag.contains("ConnectedCabinets")) {
            CompoundTag cabinetsTag = tag.getCompound("ConnectedCabinets");
            int count = cabinetsTag.getInt("CabinetCount");
            connectedCabinets.clear();
            for (int i = 0; i < count; i++) {
                if (cabinetsTag.contains("Cabinet" + i)) {
                    BlockPos pos = BlockPos.of(cabinetsTag.getLong("Cabinet" + i));
                    connectedCabinets.add(pos);
                }
            }
        }

        // Mark network as dirty to refresh on next tick
        markNetworkDirty();
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

    private class NetworkItemHandler implements IItemHandlerModifiable {
        private List<ItemStack> virtualSlots = new ArrayList<>();
        private boolean slotsCached = false;

        private void rebuildVirtualSlots() {
            virtualSlots.clear();

            // Add items from Filing Cabinets
            for (BlockPos cabinetPos : connectedCabinets) {
                BlockEntity be = level.getBlockEntity(cabinetPos);
                if (be instanceof FilingCabinetBlockEntity cabinet) {
                    IItemHandler handler = cabinet.getCapabilityHandler(null);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack stack = handler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                virtualSlots.add(stack.copy());
                            }
                        }
                    }
                }
            }

            // Add fluids from Fluid Cabinets as virtual bucket items
            for (BlockPos cabinetPos : connectedCabinets) {
                BlockEntity be = level.getBlockEntity(cabinetPos);
                if (be instanceof FluidCabinetBlockEntity fluidCabinet) {
                    // Access the internal inventory directly to read canisters
                    for (int i = 0; i < fluidCabinet.inventory.getSlots(); i++) {
                        ItemStack canisterStack = fluidCabinet.inventory.getStackInSlot(i);
                        if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                            FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
                            if (contents != null && contents.storedFluidId().isPresent() && contents.amount() > 0) {
                                // Convert fluid to bucket item
                                ItemStack bucketStack = FluidHelper.getBucketForFluid(contents.storedFluidId().get());
                                if (!bucketStack.isEmpty()) {
                                    // Set count to number of buckets worth of fluid (ignore max stack size)
                                    int bucketCount = contents.amount() / 1000;
                                    if (bucketCount > 0) {
                                        bucketStack.setCount(bucketCount); // Don't cap at max stack size!
                                        virtualSlots.add(bucketStack);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            slotsCached = true;
        }

        private void invalidateCache() {
            slotsCached = false;
        }

        @Override
        public int getSlots() {
            if (!slotsCached) {
                rebuildVirtualSlots();
            }
            return virtualSlots.size();
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            if (!slotsCached) {
                rebuildVirtualSlots();
            }
            if (slot >= 0 && slot < virtualSlots.size()) {
                return virtualSlots.get(slot).copy();
            }
            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // For virtual slots, we can't insert directly - would need complex logic
            // to determine which cabinet to route to. For now, disable insertion.
            return stack;
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // For virtual slots, extraction is view-only for now
            // Would need complex logic to extract from the correct source
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (!slotsCached) {
                rebuildVirtualSlots();
            }
            if (slot >= 0 && slot < virtualSlots.size()) {
                return virtualSlots.get(slot).getMaxStackSize();
            }
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // Virtual slots are read-only for now
            return false;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            // Virtual slots are read-only for now
        }
    }
}
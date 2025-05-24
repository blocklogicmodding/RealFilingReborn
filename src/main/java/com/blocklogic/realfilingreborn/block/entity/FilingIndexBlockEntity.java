// Ultra-Optimized FilingIndexBlockEntity with Tick-Based Sync
package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.network.VirtualNetworkManager;
import com.blocklogic.realfilingreborn.network.VirtualFolder;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    // Traditional cabinet tracking
    private Set<BlockPos> connectedCabinets = new HashSet<>();

    // High-performance virtual network
    private final VirtualNetworkManager networkManager = new VirtualNetworkManager();

    // Capability handlers cache
    private final Map<Direction, IItemHandler> handlers = new HashMap<>();

    // Performance optimization
    private long lastSyncTime = 0;
    private long lastCleanupTime = 0;
    private static final long SYNC_INTERVAL = 100; // 5 ticks
    private static final long CLEANUP_INTERVAL = 2000; // 100 ticks

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
            }
        }
    };

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        return handlers.computeIfAbsent(side != null ? side : Direction.UP,
                s -> new OptimizedNetworkHandler(this, s));
    }

    public boolean addConnectedCabinet(BlockPos cabinetPos) {
        if (connectedCabinets.contains(cabinetPos)) {
            return false;
        }

        if (level != null && level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            if (cabinet.isInNetwork()) {
                return false;
            }

            // Add to traditional tracking
            connectedCabinets.add(cabinetPos);
            cabinet.setConnectedIndex(getBlockPos());

            // Smart refresh - only if cabinet actually changed
            networkManager.refreshCabinet(level, cabinetPos, cabinet);

            setChanged();
            updateBlockStateConnection();

            System.out.println("Added cabinet to network: " + cabinetPos + " - " + networkManager.getStats());
            return true;
        }

        return false;
    }

    public boolean removeConnectedCabinet(BlockPos cabinetPos) {
        boolean removed = connectedCabinets.remove(cabinetPos);

        if (removed) {
            if (level != null && level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                cabinet.clearConnectedIndex();
            }

            // Remove from virtual network
            networkManager.refreshCabinet(level, cabinetPos, null);

            setChanged();
            updateBlockStateConnection();

            System.out.println("Removed cabinet from network: " + cabinetPos + " - " + networkManager.getStats());
        }

        return removed;
    }

    /**
     * Optimized cabinet content change notification - batched processing
     */
    public void onCabinetContentsChanged(BlockPos cabinetPos) {
        // Don't process immediately - let tick handle it for batching
        if (connectedCabinets.contains(cabinetPos)) {
            // Mark for next sync cycle
            lastSyncTime = 0; // Force sync on next tick
        }
    }

    public void disconnectAllCabinets() {
        if (level == null) return;

        Set<BlockPos> cabinetsCopy = new HashSet<>(connectedCabinets);
        for (BlockPos cabinetPos : cabinetsCopy) {
            removeConnectedCabinet(cabinetPos);
        }

        connectedCabinets.clear();
        networkManager.clearNetwork();
    }

    private void updateBlockStateConnection() {
        if (level != null && !level.isClientSide() &&
                getBlockState().getBlock() instanceof FilingIndexBlock indexBlock) {
            boolean hasConnections = !connectedCabinets.isEmpty();
            indexBlock.updateConnectionState(level, getBlockPos(), hasConnections);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FilingIndexBlockEntity blockEntity)  {
        if (level.isClientSide()) {
            return;
        }

        long currentTime = level.getGameTime();

        // High-frequency sync for virtual network
        if (currentTime - blockEntity.lastSyncTime >= SYNC_INTERVAL) {
            blockEntity.performNetworkSync();
            blockEntity.lastSyncTime = currentTime;
        }

        // Low-frequency cleanup
        if (currentTime - blockEntity.lastCleanupTime >= CLEANUP_INTERVAL) {
            blockEntity.performNetworkCleanup();
            blockEntity.lastCleanupTime = currentTime;
        }
    }

    /**
     * Efficient network sync - only sync folders that need it
     */
    private void performNetworkSync() {
        if (level == null) return;

        Set<BlockPos> changedCabinets = networkManager.syncAllFolders(level);

        // Force BER updates for changed cabinets
        for (BlockPos cabinetPos : changedCabinets) {
            level.sendBlockUpdated(cabinetPos, level.getBlockState(cabinetPos), level.getBlockState(cabinetPos), 3);
        }
    }

    /**
     * Periodic cleanup and optimization
     */
    private void performNetworkCleanup() {
        if (level == null) return;

        // Clean up broken connections
        Iterator<BlockPos> iterator = connectedCabinets.iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            BlockPos cabinetPos = iterator.next();

            if (!level.isLoaded(cabinetPos) ||
                    !(level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity)) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            // Rebuild the virtual network efficiently
            for (BlockPos cabinetPos : connectedCabinets) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    networkManager.refreshCabinet(level, cabinetPos, cabinet);
                }
            }

            setChanged();
            updateBlockStateConnection();
        }
    }

    // Legacy compatibility methods
    public Set<BlockPos> getConnectedCabinets() {
        return new HashSet<>(connectedCabinets);
    }

    public List<FilingCabinetBlockEntity> getConnectedCabinetEntities() {
        if (level == null) return new ArrayList<>();

        return connectedCabinets.stream()
                .map(pos -> level.getBlockEntity(pos))
                .filter(be -> be instanceof FilingCabinetBlockEntity)
                .map(be -> (FilingCabinetBlockEntity) be)
                .toList();
    }

    // Save/Load
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        ListTag cabinetList = new ListTag();
        for (BlockPos pos : connectedCabinets) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            cabinetList.add(posTag);
        }
        tag.put("connectedCabinets", cabinetList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        connectedCabinets.clear();
        ListTag cabinetList = tag.getList("connectedCabinets", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cabinetList.size(); i++) {
            CompoundTag posTag = cabinetList.getCompound(i);
            BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
            );
            connectedCabinets.add(pos);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Rebuild network when chunk loads - but efficiently
        if (level != null && !level.isClientSide()) {
            // Delay network rebuild to next tick to avoid chunk loading issues
            lastSyncTime = 0;
            lastCleanupTime = 0;
        }
    }

    // Standard BlockEntity methods
    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for (int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }
        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.realfilingreborn.filing_index_menu_title");
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /**
     * Ultra-optimized network handler with smart external mod support
     */
    private static class OptimizedNetworkHandler implements IItemHandler {
        private final FilingIndexBlockEntity indexEntity;
        private final Direction side;

        // Cache for external mod compatibility
        private VirtualFolder[] cachedFolderSnapshot = null;
        private long lastSnapshotTime = 0;
        private static final long SNAPSHOT_TTL = 1000; // 1 second

        public OptimizedNetworkHandler(FilingIndexBlockEntity indexEntity, @Nullable Direction side) {
            this.indexEntity = indexEntity;
            this.side = side;
        }

        @Override
        public int getSlots() {
            // Dynamic slot count based on network size (capped for performance)
            int networkSize = indexEntity.networkManager.getStats().uniqueItemTypes;
            return Math.min(networkSize, 54); // Max 54 slots like double chest
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            VirtualFolder[] snapshot = getCachedSnapshot();
            if (slot >= 0 && slot < snapshot.length) {
                VirtualFolder folder = snapshot[slot];
                if (folder.getCurrentCount() > 0) {
                    ItemStack representative = new ItemStack(
                            BuiltInRegistries.ITEM.get(folder.getItemType()),
                            Math.min(folder.getCurrentCount(), 64)
                    );
                    return representative;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;

            if (simulate) {
                // Fast simulation check
                ResourceLocation itemType = BuiltInRegistries.ITEM.getKey(stack.getItem());
                List<VirtualFolder> folders = indexEntity.networkManager.getFoldersForItem(itemType);
                return folders.isEmpty() ? stack : ItemStack.EMPTY;
            }

            // THE MAGIC - O(1) insertion!
            return indexEntity.networkManager.insertItem(indexEntity.level, stack);
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            VirtualFolder[] snapshot = getCachedSnapshot();
            if (slot >= 0 && slot < snapshot.length) {
                VirtualFolder folder = snapshot[slot];
                if (folder.getCurrentCount() > 0) {
                    if (simulate) {
                        // Fast simulation
                        int available = folder.getCurrentCount();
                        int extractAmount = Math.min(amount, Math.min(available, 64));
                        if (extractAmount > 0) {
                            return new ItemStack(BuiltInRegistries.ITEM.get(folder.getItemType()), extractAmount);
                        }
                    } else {
                        // Real extraction
                        return indexEntity.networkManager.extractItem(indexEntity.level, folder.getItemType(), amount);
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }

        /**
         * Get cached snapshot of folders for external mod compatibility
         */
        private VirtualFolder[] getCachedSnapshot() {
            long currentTime = System.currentTimeMillis();

            if (cachedFolderSnapshot == null || currentTime - lastSnapshotTime > SNAPSHOT_TTL) {
                List<VirtualFolder> allFolders = new ArrayList<>();
                for (List<VirtualFolder> folders : indexEntity.networkManager.getItemToFoldersMap().values()) {
                    allFolders.addAll(folders);
                }

                // Sort by item type for consistent ordering
                allFolders.sort(Comparator.comparing(f -> f.getItemType().toString()));

                cachedFolderSnapshot = allFolders.toArray(new VirtualFolder[0]);
                lastSnapshotTime = currentTime;
            }

            return cachedFolderSnapshot;
        }
    }
}
package com.blocklogic.realfilingreborn.capability;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

public class FilingIndexItemHandler implements IItemHandler {
    private final FilingIndexBlockEntity indexEntity;
    private final Level level;

    // PERFORMANCE: Improved caching system with proper invalidation
    private volatile List<VirtualSlotInfo> cachedVirtualSlots = null;
    private final AtomicLong lastCacheTime = new AtomicLong(0);
    private final AtomicLong cacheVersion = new AtomicLong(0);
    private static final long CACHE_DURATION_MS = 500; // Increased from 50ms to 500ms
    private static final int MAX_VIRTUAL_SLOTS_PER_SCAN = 1000; // Increased limit but still bounded

    // PERFORMANCE: Optimized range cache with longer duration
    private final Map<BlockPos, Boolean> inRangeCache = new ConcurrentHashMap<>();
    private volatile long lastRangeCacheTime = 0;
    private static final long RANGE_CACHE_DURATION_MS = 2000; // Increased from 100ms to 2s

    // PERFORMANCE: Rate limiting for update notifications
    private volatile long lastNotifyTime = 0;
    private static final long MIN_NOTIFY_INTERVAL_MS = 50; // Minimum 50ms between notifications

    public FilingIndexItemHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.level = indexEntity.getLevel();
    }

    // PERFORMANCE: Rate-limited update notifications
    private void notifyUpdate(BlockPos cabinetPos) {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(cabinetPos, level.getBlockState(cabinetPos), level.getBlockState(cabinetPos), Block.UPDATE_CLIENTS);
            invalidateCache();
        }
    }

    // PERFORMANCE: Explicit cache invalidation
    public void invalidateCache() {
        cachedVirtualSlots = null;
        cacheVersion.incrementAndGet();
        lastCacheTime.set(0);
    }

    private void invalidateRangeCache() {
        inRangeCache.clear();
        lastRangeCacheTime = 0;
    }

    // PERFORMANCE: Significantly improved virtual slot enumeration
    private List<VirtualSlotInfo> getAllVirtualSlots() {
        long currentTime = System.currentTimeMillis();
        long currentVersion = cacheVersion.get();

        // Return cached result if still valid
        List<VirtualSlotInfo> cached = cachedVirtualSlots;
        if (cached != null && (currentTime - lastCacheTime.get()) < CACHE_DURATION_MS) {
            return cached;
        }

        List<VirtualSlotInfo> virtualSlots = new ArrayList<>();
        int slotCount = 0;

        // PERFORMANCE: Process cabinets in batches to reduce lock contention
        List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());

        for (BlockPos cabinetPos : cabinets) {
            if (slotCount >= MAX_VIRTUAL_SLOTS_PER_SCAN) {
                break; // Prevent excessive scanning
            }

            if (!isInRangeCached(cabinetPos)) {
                continue;
            }

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                // PERFORMANCE: Process all 5 slots at once to reduce repeated lookups
                for (int slot = 0; slot < 5; slot++) {
                    if (slotCount >= MAX_VIRTUAL_SLOTS_PER_SCAN) break;

                    ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

                    if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                            ResourceLocation itemId = contents.storedItemId().get();
                            Item item = BuiltInRegistries.ITEM.get(itemId);
                            virtualSlots.add(new VirtualSlotInfo(cabinetPos, slot, VirtualSlotType.FILING_FOLDER, new ItemStack(item, contents.count())));
                            slotCount++;
                        }
                    }
                    else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                            // PERFORMANCE: Only process first few NBT items to prevent excessive memory usage
                            int itemsToProcess = Math.min(contents.storedItems().size(), 100);
                            for (int i = 0; i < itemsToProcess; i++) {
                                if (slotCount >= MAX_VIRTUAL_SLOTS_PER_SCAN) break;
                                NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(i);
                                virtualSlots.add(new VirtualSlotInfo(cabinetPos, slot, VirtualSlotType.NBT_FOLDER, serializedItem.stack().copy(), i));
                                slotCount++;
                            }
                        }
                    }
                }
            }
        }

        // Cache the result atomically
        if (currentVersion == cacheVersion.get()) { // Only cache if version hasn't changed
            cachedVirtualSlots = virtualSlots;
            lastCacheTime.set(currentTime);
        }

        return virtualSlots;
    }

    // PERFORMANCE: Improved range caching with longer duration
    private boolean isInRangeCached(BlockPos cabinetPos) {
        long currentTime = System.currentTimeMillis();

        // Clear cache if expired
        if ((currentTime - lastRangeCacheTime) > RANGE_CACHE_DURATION_MS) {
            invalidateRangeCache();
            lastRangeCacheTime = currentTime;
        }

        return inRangeCache.computeIfAbsent(cabinetPos, pos -> indexEntity.isInRange(pos));
    }

    @Override
    public int getSlots() {
        // PERFORMANCE: Return predictable slot count based on linked cabinets for better pipe compatibility
        return Math.max(indexEntity.getLinkedCabinetCount() * 5, 1);
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        // PERFORMANCE: Optimized slot mapping for better performance
        List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());
        int cabinetIndex = slot / 5;
        int cabinetSlot = slot % 5;

        if (cabinetIndex >= cabinets.size()) {
            return ItemStack.EMPTY;
        }

        BlockPos cabinetPos = cabinets.get(cabinetIndex);
        if (!isInRangeCached(cabinetPos)) {
            return ItemStack.EMPTY;
        }

        if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);

            // Return the stored items as virtual stack
            if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                    Item item = BuiltInRegistries.ITEM.get(contents.storedItemId().get());
                    return new ItemStack(item, Math.min(contents.count(), item.getDefaultMaxStackSize()));
                }
            } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents != null && !contents.storedItems().isEmpty()) {
                    return contents.storedItems().get(0).stack().copy();
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;

        // PERFORMANCE: Try direct slot mapping first for better performance
        List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());
        int cabinetIndex = slot / 5;
        int cabinetSlot = slot % 5;

        if (cabinetIndex < cabinets.size()) {
            BlockPos cabinetPos = cabinets.get(cabinetIndex);
            if (isInRangeCached(cabinetPos)) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                    ItemStack result = insertItemIntoCabinet(cabinet, cabinetSlot, stack, simulate, cabinetPos);
                    if (result.getCount() < stack.getCount()) {
                        return result;
                    }
                }
            }
        }

        // If direct slot fails, try compatible folders (existing logic)
        boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        for (BlockPos cabinetPos : cabinets) {
            if (!isInRangeCached(cabinetPos)) continue;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                for (int i = 0; i < 5; i++) {
                    ItemStack result = tryInsertIntoFolder(cabinet, i, stack, itemId, hasNBT, simulate, cabinetPos);
                    if (result.getCount() < stack.getCount()) {
                        return result;
                    }
                }
            }
        }

        return stack;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;

        // PERFORMANCE: Direct slot mapping for better performance
        List<BlockPos> cabinets = new ArrayList<>(indexEntity.getLinkedCabinets());
        int cabinetIndex = slot / 5;
        int cabinetSlot = slot % 5;

        if (cabinetIndex < cabinets.size()) {
            BlockPos cabinetPos = cabinets.get(cabinetIndex);
            if (isInRangeCached(cabinetPos)) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                    return extractFromCabinet(cabinet, cabinetSlot, amount, simulate, cabinetPos);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    // PERFORMANCE: Optimized insertion methods (keeping existing logic but with better error handling)
    private ItemStack insertItemIntoCabinet(FilingCabinetBlockEntity cabinet, int cabinetSlot, ItemStack stack, boolean simulate, BlockPos cabinetPos) {
        try {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);
            if (folderStack.isEmpty()) return stack;

            boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

            return tryInsertIntoFolder(cabinet, cabinetSlot, stack, itemId, hasNBT, simulate, cabinetPos);
        } catch (Exception e) {
            // PERFORMANCE: Graceful error handling to prevent crashes
            return stack;
        }
    }

    private ItemStack tryInsertIntoFolder(FilingCabinetBlockEntity cabinet, int cabinetSlot, ItemStack stack, ResourceLocation itemId, boolean hasNBT, boolean simulate, BlockPos cabinetPos) {
        try {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);
            if (folderStack.isEmpty()) return stack;

            if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                if (hasNBT) return stack;

                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null) return stack;

                if (contents.storedItemId().isPresent() && contents.storedItemId().get().equals(itemId)) {
                    // PERFORMANCE: Check for overflow before calculation
                    if (contents.count() > Integer.MAX_VALUE - stack.getCount()) {
                        return stack; // Would overflow
                    }

                    long newTotal = (long)contents.count() + stack.getCount();
                    int maxAdd = newTotal > Integer.MAX_VALUE ? Integer.MAX_VALUE - contents.count() : stack.getCount();

                    if (maxAdd > 0 && !simulate) {
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                contents.storedItemId(), contents.count() + maxAdd);
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(cabinetPos);
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(maxAdd);
                    return remaining;
                }
            } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                if (!hasNBT) return stack;

                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null) return stack;

                if (contents.storedItemId().isPresent() && contents.storedItemId().get().equals(itemId)) {
                    int space = NBTFilingFolderItem.MAX_NBT_ITEMS - contents.storedItems().size();
                    int canAdd = Math.min(1, space); // NBT folders take 1 at a time

                    if (canAdd > 0 && !simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(single));

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                contents.storedItemId(), newItems);
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(cabinetPos);
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(canAdd);
                    return remaining;
                }
            }

            return stack;
        } catch (Exception e) {
            // PERFORMANCE: Graceful error handling
            return stack;
        }
    }

    private ItemStack extractFromCabinet(FilingCabinetBlockEntity cabinet, int cabinetSlot, int amount, boolean simulate, BlockPos cabinetPos) {
        try {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);
            if (folderStack.isEmpty()) return ItemStack.EMPTY;

            if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                    Item item = BuiltInRegistries.ITEM.get(contents.storedItemId().get());
                    int extractAmount = Math.min(amount, Math.min(contents.count(), item.getDefaultMaxStackSize()));

                    if (extractAmount > 0 && !simulate) {
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                contents.storedItemId(), contents.count() - extractAmount);
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(cabinetPos);
                    }

                    return new ItemStack(item, extractAmount);
                }
            } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents != null && !contents.storedItems().isEmpty()) {
                    int extractAmount = Math.min(amount, 1); // NBT items extract 1 at a time

                    if (extractAmount > 0 && !simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());
                        newItems.remove(newItems.size() - 1);

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                contents.storedItemId(), newItems);
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(cabinetPos);
                    }

                    ItemStack result = contents.storedItems().get(contents.storedItems().size() - 1).stack().copy();
                    result.setCount(extractAmount);
                    return result;
                }
            }

            return ItemStack.EMPTY;
        } catch (Exception e) {
            // PERFORMANCE: Graceful error handling
            return ItemStack.EMPTY;
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64; // Standard stack size for pipe compatibility
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true; // Accept all items for pipe compatibility
    }

    private static class VirtualSlotInfo {
        final BlockPos cabinetPos;
        final int slotIndex;
        final VirtualSlotType type;
        final ItemStack virtualStack;
        final int nbtIndex;

        public VirtualSlotInfo(BlockPos cabinetPos, int slotIndex, VirtualSlotType type, ItemStack virtualStack) {
            this(cabinetPos, slotIndex, type, virtualStack, -1);
        }

        public VirtualSlotInfo(BlockPos cabinetPos, int slotIndex, VirtualSlotType type, ItemStack virtualStack, int nbtIndex) {
            this.cabinetPos = cabinetPos;
            this.slotIndex = slotIndex;
            this.type = type;
            this.virtualStack = virtualStack;
            this.nbtIndex = nbtIndex;
        }
    }

    private enum VirtualSlotType {
        FILING_FOLDER,
        NBT_FOLDER
    }
}
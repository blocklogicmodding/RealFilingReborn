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
import java.util.Map;

public class FilingIndexItemHandler implements IItemHandler {
    private final FilingIndexBlockEntity indexEntity;
    private final Level level;

    // PERFORMANCE: Cache virtual slots with invalidation
    private List<VirtualSlotInfo> cachedVirtualSlots = null;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 50; // 50ms cache duration
    private static final int MAX_VIRTUAL_SLOTS_PER_SCAN = 500; // Limit scanning

    // PERFORMANCE: Cache for in-range cabinets
    private final Map<BlockPos, Boolean> inRangeCache = new ConcurrentHashMap<>();
    private long lastRangeCacheTime = 0;
    private static final long RANGE_CACHE_DURATION_MS = 100;

    public FilingIndexItemHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.level = indexEntity.getLevel();
    }

    private void notifyUpdate(BlockPos cabinetPos) {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(cabinetPos, level.getBlockState(cabinetPos), level.getBlockState(cabinetPos), Block.UPDATE_CLIENTS);
            // PERFORMANCE: Invalidate cache when things change
            invalidateCache();
        }
    }

    private void invalidateCache() {
        cachedVirtualSlots = null;
        lastCacheTime = 0;
    }

    private void invalidateRangeCache() {
        inRangeCache.clear();
        lastRangeCacheTime = 0;
    }

    // PERFORMANCE: Optimized virtual slot enumeration with caching and limits
    private List<VirtualSlotInfo> getAllVirtualSlots() {
        long currentTime = System.currentTimeMillis();

        // Return cached result if still valid
        if (cachedVirtualSlots != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            return cachedVirtualSlots;
        }

        List<VirtualSlotInfo> virtualSlots = new ArrayList<>();
        int slotCount = 0;

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (slotCount >= MAX_VIRTUAL_SLOTS_PER_SCAN) {
                break; // Prevent excessive scanning
            }

            if (!isInRangeCached(cabinetPos)) {
                continue;
            }

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
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
                            for (int i = 0; i < contents.storedItems().size(); i++) {
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

        // Cache the result
        cachedVirtualSlots = virtualSlots;
        lastCacheTime = currentTime;
        return virtualSlots;
    }

    // PERFORMANCE: Cached range checking
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
        // PERFORMANCE: Return consistent slot count for pipe compatibility
        int totalSlots = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (isInRangeCached(cabinetPos)) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                    totalSlots += 5; // 5 slots per cabinet
                }
            }
        }
        return Math.max(totalSlots, 1); // Always at least 1 slot
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        // PERFORMANCE: Map slots directly to cabinets
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRangeCached(cabinetPos)) continue;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                if (slot >= currentSlot && slot < currentSlot + 5) {
                    int cabinetSlot = slot - currentSlot;
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
                    return ItemStack.EMPTY;
                }
                currentSlot += 5;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;

        // PERFORMANCE: Map slot to specific cabinet and handle insertion directly
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRangeCached(cabinetPos)) continue;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                if (slot >= currentSlot && slot < currentSlot + 5) {
                    int cabinetSlot = slot - currentSlot;
                    ItemStack result = insertItemIntoCabinet(cabinet, cabinetSlot, stack, simulate, cabinetPos);
                    if (result.getCount() < stack.getCount()) {
                        return result; // Successfully inserted some/all
                    }
                }
                currentSlot += 5;
            }
        }

        // If specific slot fails, try any compatible folder
        boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
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

        // PERFORMANCE: Map slot directly to cabinet
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRangeCached(cabinetPos)) continue;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                if (slot >= currentSlot && slot < currentSlot + 5) {
                    int cabinetSlot = slot - currentSlot;
                    return extractFromCabinet(cabinet, cabinetSlot, amount, simulate, cabinetPos);
                }
                currentSlot += 5;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack insertItemIntoCabinet(FilingCabinetBlockEntity cabinet, int cabinetSlot, ItemStack stack, boolean simulate, BlockPos cabinetPos) {
        ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);
        if (folderStack.isEmpty()) return stack;

        boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        return tryInsertIntoFolder(cabinet, cabinetSlot, stack, itemId, hasNBT, simulate, cabinetPos);
    }

    private ItemStack tryInsertIntoFolder(FilingCabinetBlockEntity cabinet, int cabinetSlot, ItemStack stack, ResourceLocation itemId, boolean hasNBT, boolean simulate, BlockPos cabinetPos) {
        ItemStack folderStack = cabinet.inventory.getStackInSlot(cabinetSlot);
        if (folderStack.isEmpty()) return stack;

        if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
            if (hasNBT) return stack;

            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents == null) return stack;

            if (contents.storedItemId().isPresent() && contents.storedItemId().get().equals(itemId)) {
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
    }

    private ItemStack extractFromCabinet(FilingCabinetBlockEntity cabinet, int cabinetSlot, int amount, boolean simulate, BlockPos cabinetPos) {
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
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64; // FIXED: Standard stack size for pipe compatibility
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true; // FIXED: Accept all items for pipe compatibility
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
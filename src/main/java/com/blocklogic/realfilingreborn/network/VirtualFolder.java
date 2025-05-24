// Optimized Virtual Folder with Hot Path Avoidance
package com.blocklogic.realfilingreborn.network;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * High-performance virtual folder that avoids inventory access in hot paths
 */
public class VirtualFolder {
    private final UUID folderId;
    private final BlockPos cabinetPos;
    private final int slotIndex;
    private final ResourceLocation itemType;
    private final FolderType type;

    // Hot path cached data - updated only when needed
    private volatile int currentCount;
    private volatile int maxCapacity;
    private volatile long lastSyncTime;
    private volatile boolean needsSync;

    // Performance tracking
    private long totalInsertions = 0;
    private long totalExtractions = 0;

    public enum FolderType {
        STANDARD(Integer.MAX_VALUE),
        NBT(64);

        private final int defaultCapacity;
        FolderType(int defaultCapacity) { this.defaultCapacity = defaultCapacity; }
        public int getDefaultCapacity() { return defaultCapacity; }
    }

    public VirtualFolder(UUID folderId, BlockPos cabinetPos, int slotIndex,
                         ResourceLocation itemType, FolderType type) {
        this.folderId = folderId;
        this.cabinetPos = cabinetPos;
        this.slotIndex = slotIndex;
        this.itemType = itemType;
        this.type = type;
        this.currentCount = 0;
        this.maxCapacity = type.getDefaultCapacity();
        this.lastSyncTime = System.currentTimeMillis();
        this.needsSync = true;
    }

    /**
     * Hot path - check if item can be accepted WITHOUT accessing real inventory
     */
    public boolean canAcceptFast(ResourceLocation stackItemType, boolean hasNBT, int stackSize) {
        // Quick type check
        if (!Objects.equals(itemType, stackItemType)) {
            return false;
        }

        // Quick NBT compatibility check
        if (type == FolderType.NBT && !hasNBT) return false;
        if (type == FolderType.STANDARD && hasNBT) return false;

        // Quick space check using cached data
        return getAvailableSpace() > 0;
    }

    /**
     * Fast insertion that batches real inventory updates
     */
    public ItemStack insertFast(int amount) {
        if (amount <= 0 || currentCount >= maxCapacity) {
            return createRemainingStack(amount);
        }

        // Calculate insertion based on cached data
        int availableSpace = getAvailableSpace();
        int toInsert = Math.min(amount, availableSpace);

        if (toInsert > 0) {
            // Update cached data immediately for hot path performance
            currentCount += toInsert;
            totalInsertions += toInsert;
            needsSync = true;

            // Return remaining
            int remaining = amount - toInsert;
            return remaining > 0 ? createRemainingStack(remaining) : ItemStack.EMPTY;
        }

        return createRemainingStack(amount);
    }

    /**
     * Fast extraction using cached data
     */
    public ItemStack extractFast(int amount) {
        if (amount <= 0 || currentCount <= 0) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.get(itemType);
        int extractAmount = Math.min(Math.min(currentCount, amount), 64);

        if (extractAmount > 0) {
            // Update cached data immediately
            currentCount -= extractAmount;
            totalExtractions += extractAmount;
            needsSync = true;

            return new ItemStack(item, extractAmount);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Sync virtual data with real inventory - called on ticks, not hot paths
     */
    public boolean syncWithRealInventory(Level level) {
        if (!needsSync && (System.currentTimeMillis() - lastSyncTime) < 1000) {
            return false; // Don't sync too frequently
        }

        if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotIndex);

            if (folderStack.isEmpty()) {
                // Folder was removed
                return false;
            }

            boolean changed = false;

            if (type == FolderType.STANDARD && folderStack.getItem() instanceof FilingFolderItem) {
                changed = syncStandardFolder(folderStack, level);
            } else if (type == FolderType.NBT && folderStack.getItem() instanceof NBTFilingFolderItem) {
                changed = syncNBTFolder(folderStack, level);
            }

            if (changed) {
                cabinet.setChanged();
                level.sendBlockUpdated(cabinetPos, cabinet.getBlockState(), cabinet.getBlockState(), 3);
            }

            needsSync = false;
            lastSyncTime = System.currentTimeMillis();
            return changed;
        }

        return false;
    }

    private boolean syncStandardFolder(ItemStack folderStack, Level level) {
        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
        if (contents == null) return false;

        // Update real inventory to match virtual state
        if (contents.count() != currentCount) {
            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                    contents.storedItemId(), currentCount
            );
            folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
            return true;
        }

        return false;
    }

    private boolean syncNBTFolder(ItemStack folderStack, Level level) {
        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
        if (contents == null) return false;

        // For NBT folders, we need to maintain the actual item list
        // This is more complex and might need special handling
        int realCount = contents.storedItems().size();

        if (realCount != currentCount) {
            // Sync discrepancy - need to resolve
            // For now, trust the real inventory
            currentCount = realCount;
            return false;
        }

        return false;
    }

    /**
     * Load virtual state from real inventory (initialization)
     */
    public void loadFromRealInventory(Level level) {
        if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotIndex);

            if (type == FolderType.STANDARD && folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null) {
                    currentCount = contents.count();
                }
            } else if (type == FolderType.NBT && folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents != null) {
                    currentCount = contents.storedItems().size();
                }
            }
        }

        needsSync = false;
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Get hash for change detection
     */
    public int getContentHash() {
        return Objects.hash(itemType, currentCount, type);
    }

    public int getAvailableSpace() {
        return Math.max(0, maxCapacity - currentCount);
    }

    public double getUtilization() {
        return maxCapacity > 0 ? (double) currentCount / maxCapacity : 0.0;
    }

    public boolean hasSignificantLoad() {
        return totalInsertions > 1000 || totalExtractions > 1000;
    }

    private ItemStack createRemainingStack(int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(itemType), amount);
    }

    private boolean hasSignificantNBT(ItemStack stack) {
        return NBTFilingFolderItem.hasSignificantNBT(stack);
    }

    // Getters
    public UUID getFolderId() { return folderId; }
    public BlockPos getCabinetPos() { return cabinetPos; }
    public int getSlotIndex() { return slotIndex; }
    public ResourceLocation getItemType() { return itemType; }
    public FolderType getType() { return type; }
    public int getCurrentCount() { return currentCount; }
    public long getTotalInsertions() { return totalInsertions; }
    public long getTotalExtractions() { return totalExtractions; }
    public boolean needsSync() { return needsSync; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualFolder that)) return false;
        return Objects.equals(folderId, that.folderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId);
    }

    @Override
    public String toString() {
        return String.format("VirtualFolder{%s:%d, count=%d/%d, ins=%d, ext=%d}",
                itemType, slotIndex, currentCount, maxCapacity, totalInsertions, totalExtractions);
    }
}
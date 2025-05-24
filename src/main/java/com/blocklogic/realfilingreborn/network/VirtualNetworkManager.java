// High-Performance Network Manager with Change Detection and Load Balancing
package com.blocklogic.realfilingreborn.network;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ultra-optimized network manager with hot path avoidance and smart load balancing
 */
public class VirtualNetworkManager {
    // Core lookup tables
    private final Map<ResourceLocation, List<VirtualFolder>> itemToFoldersMap = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualFolder> folderRegistry = new ConcurrentHashMap<>();
    private final Map<BlockPos, CabinetInfo> cabinetRegistry = new ConcurrentHashMap<>();

    // Performance optimization
    private final Map<ResourceLocation, VirtualFolder[]> cachedFolderArrays = new ConcurrentHashMap<>();
    private volatile long lastArrayCacheUpdate = 0;
    private static final long ARRAY_CACHE_TTL = 5000; // 5 seconds

    // Statistics and monitoring
    private volatile long totalInsertions = 0;
    private volatile long totalExtractions = 0;
    private volatile long lastOptimizationTime = 0;
    private static final long OPTIMIZATION_INTERVAL = 10000; // 10 seconds

    /**
     * Cabinet information with change detection
     */
    private static class CabinetInfo {
        final BlockPos position;
        final Set<UUID> folderIds = new HashSet<>();
        volatile int contentHash;
        volatile long lastUpdateTime;

        CabinetInfo(BlockPos position) {
            this.position = position;
            this.contentHash = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        boolean hasChanged(int newHash) {
            return contentHash != newHash;
        }

        void updateHash(int newHash) {
            contentHash = newHash;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Register folder with change detection
     */
    public boolean registerFolder(BlockPos cabinetPos, int slotIndex, ResourceLocation itemType,
                                  VirtualFolder.FolderType type, Level level) {

        UUID folderId = generateFolderId(cabinetPos, slotIndex);

        // Check if folder already exists
        VirtualFolder existingFolder = folderRegistry.get(folderId);
        if (existingFolder != null) {
            // Update existing folder
            existingFolder.loadFromRealInventory(level);
            return false; // No structural change
        }

        // Create new virtual folder
        VirtualFolder virtualFolder = new VirtualFolder(folderId, cabinetPos, slotIndex, itemType, type);
        virtualFolder.loadFromRealInventory(level);

        // Add to all lookup tables
        folderRegistry.put(folderId, virtualFolder);
        itemToFoldersMap.computeIfAbsent(itemType, k -> new ArrayList<>()).add(virtualFolder);

        // Update cabinet registry
        CabinetInfo cabinetInfo = cabinetRegistry.computeIfAbsent(cabinetPos, CabinetInfo::new);
        cabinetInfo.folderIds.add(folderId);

        invalidateArrayCache();

        System.out.println("Registered folder: " + itemType + " at " + cabinetPos + ":" + slotIndex);
        return true;
    }

    /**
     * Smart cabinet refresh with change detection
     */
    public boolean refreshCabinet(Level level, BlockPos cabinetPos, FilingCabinetBlockEntity cabinet) {
        CabinetInfo cabinetInfo = cabinetRegistry.get(cabinetPos);

        if (cabinet == null) {
            // Cabinet removed
            if (cabinetInfo != null) {
                unregisterAllFoldersInCabinet(cabinetPos);
                return true;
            }
            return false;
        }

        // Calculate new content hash
        int newHash = calculateCabinetHash(cabinet);

        if (cabinetInfo != null && !cabinetInfo.hasChanged(newHash)) {
            // No changes detected - skip expensive refresh
            return false;
        }

        // Content changed - perform full refresh
        unregisterAllFoldersInCabinet(cabinetPos);

        boolean hasChanges = false;
        for (int slot = 0; slot < 5; slot++) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (!folderStack.isEmpty()) {
                if (registerFolderFromStack(cabinetPos, slot, folderStack, level)) {
                    hasChanges = true;
                }
            }
        }

        // Update cabinet hash
        if (cabinetInfo == null) {
            cabinetInfo = new CabinetInfo(cabinetPos);
            cabinetRegistry.put(cabinetPos, cabinetInfo);
        }
        cabinetInfo.updateHash(newHash);

        return hasChanges;
    }

    /**
     * Ultra-fast O(1) insertion with load balancing
     */
    public ItemStack insertItem(Level level, ItemStack stack) {
        if (stack.isEmpty()) return stack;

        ResourceLocation itemType = getItemType(stack);
        boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);

        // Get folders for this item type with caching
        VirtualFolder[] compatibleFolders = getCachedFolderArray(itemType);
        if (compatibleFolders == null || compatibleFolders.length == 0) {
            return stack; // No compatible folders
        }

        totalInsertions++;

        // Load balancing: try folders in order of available space
        VirtualFolder[] sortedFolders = getSortedFoldersBySpace(compatibleFolders, hasNBT);

        ItemStack remaining = stack;
        for (VirtualFolder folder : sortedFolders) {
            if (folder.canAcceptFast(itemType, hasNBT, remaining.getCount())) {
                ItemStack result = folder.insertFast(remaining.getCount());

                if (result.getCount() < remaining.getCount()) {
                    // Successfully inserted some/all
                    remaining = result;
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
        }

        // Trigger periodic optimization
        checkForOptimization();

        return remaining;
    }

    /**
     * Fast extraction with load balancing
     */
    public ItemStack extractItem(Level level, ResourceLocation itemType, int amount) {
        VirtualFolder[] compatibleFolders = getCachedFolderArray(itemType);
        if (compatibleFolders == null || compatibleFolders.length == 0) {
            return ItemStack.EMPTY;
        }

        totalExtractions++;

        // Extract from fullest folders first to balance load
        VirtualFolder[] sortedFolders = getSortedFoldersByUtilization(compatibleFolders);

        for (VirtualFolder folder : sortedFolders) {
            ItemStack extracted = folder.extractFast(amount);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get cached folder array for O(1) access
     */
    private VirtualFolder[] getCachedFolderArray(ResourceLocation itemType) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastArrayCacheUpdate > ARRAY_CACHE_TTL) {
            rebuildArrayCache();
        }

        return cachedFolderArrays.get(itemType);
    }

    /**
     * Sort folders by available space for load balancing
     */
    private VirtualFolder[] getSortedFoldersBySpace(VirtualFolder[] folders, boolean needsNBTFolder) {
        List<VirtualFolder> validFolders = new ArrayList<>();

        for (VirtualFolder folder : folders) {
            boolean isNBTFolder = folder.getType() == VirtualFolder.FolderType.NBT;
            if ((needsNBTFolder && isNBTFolder) || (!needsNBTFolder && !isNBTFolder)) {
                validFolders.add(folder);
            }
        }

        // Sort by available space (most space first)
        validFolders.sort((a, b) -> Integer.compare(b.getAvailableSpace(), a.getAvailableSpace()));

        // Add some randomization to prevent hot-spotting
        if (validFolders.size() > 1) {
            Collections.shuffle(validFolders.subList(0, Math.min(3, validFolders.size())),
                    ThreadLocalRandom.current());
        }

        return validFolders.toArray(new VirtualFolder[0]);
    }

    /**
     * Sort folders by utilization for extraction
     */
    private VirtualFolder[] getSortedFoldersByUtilization(VirtualFolder[] folders) {
        List<VirtualFolder> validFolders = Arrays.stream(folders)
                .filter(f -> f.getCurrentCount() > 0)
                .sorted((a, b) -> Double.compare(b.getUtilization(), a.getUtilization()))
                .toList();

        return validFolders.toArray(new VirtualFolder[0]);
    }

    /**
     * Rebuild array cache for better performance
     */
    private void rebuildArrayCache() {
        cachedFolderArrays.clear();

        for (Map.Entry<ResourceLocation, List<VirtualFolder>> entry : itemToFoldersMap.entrySet()) {
            List<VirtualFolder> folders = entry.getValue();
            if (!folders.isEmpty()) {
                cachedFolderArrays.put(entry.getKey(), folders.toArray(new VirtualFolder[0]));
            }
        }

        lastArrayCacheUpdate = System.currentTimeMillis();
    }

    /**
     * Periodic optimization and maintenance
     */
    private void checkForOptimization() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastOptimizationTime > OPTIMIZATION_INTERVAL) {
            performOptimization();
            lastOptimizationTime = currentTime;
        }
    }

    /**
     * Sync all virtual folders with real inventory
     */
    public Set<BlockPos> syncAllFolders(Level level) {
        Set<BlockPos> changedCabinets = new HashSet<>();
        List<VirtualFolder> foldersToRemove = new ArrayList<>();

        for (VirtualFolder folder : folderRegistry.values()) {
            if (!folder.syncWithRealInventory(level)) {
                foldersToRemove.add(folder);
            } else if (folder.needsSync()) {
                changedCabinets.add(folder.getCabinetPos());
            }
        }

        // Remove invalid folders
        for (VirtualFolder folder : foldersToRemove) {
            unregisterFolder(folder.getCabinetPos(), folder.getSlotIndex());
        }

        if (!foldersToRemove.isEmpty()) {
            invalidateArrayCache();
        }

        return changedCabinets;
    }

    private void performOptimization() {
        // Remove empty folder lists
        itemToFoldersMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Rebuild array cache
        rebuildArrayCache();

        System.out.println("Network optimization complete - " + getStats());
    }

    private void unregisterAllFoldersInCabinet(BlockPos cabinetPos) {
        CabinetInfo cabinetInfo = cabinetRegistry.get(cabinetPos);
        if (cabinetInfo != null) {
            for (UUID folderId : new HashSet<>(cabinetInfo.folderIds)) {
                VirtualFolder folder = folderRegistry.remove(folderId);
                if (folder != null) {
                    // Remove from item mapping
                    List<VirtualFolder> folders = itemToFoldersMap.get(folder.getItemType());
                    if (folders != null) {
                        folders.remove(folder);
                        if (folders.isEmpty()) {
                            itemToFoldersMap.remove(folder.getItemType());
                        }
                    }
                }
            }
            cabinetRegistry.remove(cabinetPos);
            invalidateArrayCache();
        }
    }

    private boolean registerFolderFromStack(BlockPos cabinetPos, int slot, ItemStack folderStack, Level level) {
        if (folderStack.getItem() instanceof FilingFolderItem &&
                !(folderStack.getItem() instanceof NBTFilingFolderItem)) {

            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItemId().isPresent()) {
                return registerFolder(cabinetPos, slot, contents.storedItemId().get(),
                        VirtualFolder.FolderType.STANDARD, level);
            }
        } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItemId().isPresent()) {
                return registerFolder(cabinetPos, slot, contents.storedItemId().get(),
                        VirtualFolder.FolderType.NBT, level);
            }
        }
        return false;
    }

    private int calculateCabinetHash(FilingCabinetBlockEntity cabinet) {
        int hash = 0;
        for (int i = 0; i < 5; i++) {
            ItemStack stack = cabinet.inventory.getStackInSlot(i);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.hashCode());
        }
        return hash;
    }

    private void unregisterFolder(BlockPos cabinetPos, int slotIndex) {
        UUID folderId = generateFolderId(cabinetPos, slotIndex);
        VirtualFolder folder = folderRegistry.remove(folderId);

        if (folder != null) {
            // Remove from item mapping
            List<VirtualFolder> folders = itemToFoldersMap.get(folder.getItemType());
            if (folders != null) {
                folders.remove(folder);
                if (folders.isEmpty()) {
                    itemToFoldersMap.remove(folder.getItemType());
                }
            }

            // Remove from cabinet mapping
            CabinetInfo cabinetInfo = cabinetRegistry.get(cabinetPos);
            if (cabinetInfo != null) {
                cabinetInfo.folderIds.remove(folderId);
                if (cabinetInfo.folderIds.isEmpty()) {
                    cabinetRegistry.remove(cabinetPos);
                }
            }

            invalidateArrayCache();
        }
    }

    private void invalidateArrayCache() {
        cachedFolderArrays.clear();
        lastArrayCacheUpdate = 0;
    }

    public void clearNetwork() {
        itemToFoldersMap.clear();
        folderRegistry.clear();
        cabinetRegistry.clear();
        cachedFolderArrays.clear();
        totalInsertions = 0;
        totalExtractions = 0;
        System.out.println("Network cleared!");
    }

    public List<VirtualFolder> getFoldersForItem(ResourceLocation itemType) {
        return itemToFoldersMap.getOrDefault(itemType, new ArrayList<>());
    }

    public Map<ResourceLocation, List<VirtualFolder>> getItemToFoldersMap() {
        return Collections.unmodifiableMap(itemToFoldersMap);
    }

    public NetworkStats getStats() {
        return new NetworkStats(
                folderRegistry.size(),
                itemToFoldersMap.size(),
                cabinetRegistry.size(),
                totalInsertions,
                totalExtractions,
                System.currentTimeMillis()
        );
    }

    private ResourceLocation getItemType(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private UUID generateFolderId(BlockPos pos, int slot) {
        return UUID.nameUUIDFromBytes((pos.toString() + ":" + slot).getBytes());
    }

    public static class NetworkStats {
        public final int totalFolders;
        public final int uniqueItemTypes;
        public final int totalCabinets;
        public final long totalInsertions;
        public final long totalExtractions;
        public final long timestamp;

        public NetworkStats(int totalFolders, int uniqueItemTypes, int totalCabinets,
                            long totalInsertions, long totalExtractions, long timestamp) {
            this.totalFolders = totalFolders;
            this.uniqueItemTypes = uniqueItemTypes;
            this.totalCabinets = totalCabinets;
            this.totalInsertions = totalInsertions;
            this.totalExtractions = totalExtractions;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("Network: %d folders, %d types, %d cabinets, %d ins, %d ext",
                    totalFolders, uniqueItemTypes, totalCabinets, totalInsertions, totalExtractions);
        }
    }
}
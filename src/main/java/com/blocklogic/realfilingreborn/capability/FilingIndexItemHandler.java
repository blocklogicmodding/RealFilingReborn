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

public class FilingIndexItemHandler implements IItemHandler {
    private final FilingIndexBlockEntity indexEntity;
    private final Level level;

    public FilingIndexItemHandler(FilingIndexBlockEntity indexEntity) {
        this.indexEntity = indexEntity;
        this.level = indexEntity.getLevel();
    }

    private void notifyUpdate(BlockPos cabinetPos) {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(cabinetPos, level.getBlockState(cabinetPos), level.getBlockState(cabinetPos), Block.UPDATE_CLIENTS);
        }
    }

    private List<VirtualSlotInfo> getAllVirtualSlots() {
        List<VirtualSlotInfo> virtualSlots = new ArrayList<>();

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) {
                continue;
            }

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                for (int slot = 0; slot < 5; slot++) {
                    ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

                    if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                            ResourceLocation itemId = contents.storedItemId().get();
                            Item item = BuiltInRegistries.ITEM.get(itemId);
                            virtualSlots.add(new VirtualSlotInfo(cabinetPos, slot, VirtualSlotType.FILING_FOLDER, new ItemStack(item, contents.count())));
                        }
                    }
                    else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                            for (int i = 0; i < contents.storedItems().size(); i++) {
                                NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(i);
                                virtualSlots.add(new VirtualSlotInfo(cabinetPos, slot, VirtualSlotType.NBT_FOLDER, serializedItem.stack().copy(), i));
                            }
                        }
                    }
                }
            }
        }

        return virtualSlots;
    }

    private boolean isInRange(BlockPos cabinetPos) {
        double distance = Math.sqrt(indexEntity.getBlockPos().distSqr(cabinetPos));
        return distance <= indexEntity.getRange();
    }

    @Override
    public int getSlots() {
        // FIXED: Return consistent slot count for pipe compatibility
        int totalSlots = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (isInRange(cabinetPos)) {
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
        // FIXED: Map slots directly to cabinets
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) continue;

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

        // FIXED: Map slot to specific cabinet and handle insertion directly
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) continue;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                if (slot >= currentSlot && slot < currentSlot + 5) {
                    int cabinetSlot = slot - currentSlot;
                    return insertItemIntoCabinet(cabinet, cabinetSlot, stack, simulate, cabinetPos);
                }
                currentSlot += 5;
            }
        }

        // If specific slot fails, try any compatible folder
        boolean hasNBT = NBTFilingFolderItem.hasSignificantNBT(stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) continue;

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

        // FIXED: Map slot directly to cabinet
        int currentSlot = 0;
        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            if (!isInRange(cabinetPos)) continue;

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

    private ItemStack handleFilingFolderInsertion(VirtualSlotInfo slotInfo, ItemStack stack, boolean simulate) {
        if (level.getBlockEntity(slotInfo.cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotInfo.slotIndex);
            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItemId().isPresent()) {
                    long newCount = (long)contents.count() + stack.getCount();
                    int maxToAdd = newCount > Integer.MAX_VALUE ?
                            Integer.MAX_VALUE - contents.count() : stack.getCount();

                    if (maxToAdd > 0 && !simulate) {
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                contents.storedItemId(), contents.count() + maxToAdd);
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(slotInfo.cabinetPos);
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(maxToAdd);
                    return remaining;
                }
            }
        }
        return stack;
    }

    private ItemStack handleFilingFolderExtraction(VirtualSlotInfo slotInfo, int amount, boolean simulate) {
        if (level.getBlockEntity(slotInfo.cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotInfo.slotIndex);
            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                    ResourceLocation itemId = contents.storedItemId().get();
                    Item item = BuiltInRegistries.ITEM.get(itemId);

                    ItemStack dummyStack = new ItemStack(item);
                    int extractAmount = Math.min(amount, Math.min(contents.count(), item.getMaxStackSize(dummyStack)));

                    if (extractAmount > 0 && !simulate) {
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                contents.storedItemId(), contents.count() - extractAmount);
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(slotInfo.cabinetPos);
                    }

                    return new ItemStack(item, extractAmount);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack handleNBTFolderInsertion(VirtualSlotInfo slotInfo, ItemStack stack, boolean simulate) {
        if (level.getBlockEntity(slotInfo.cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotInfo.slotIndex);
            if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItems().size() < NBTFilingFolderItem.MAX_NBT_ITEMS) {
                    if (!simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());
                        ItemStack singleItem = stack.copy();
                        singleItem.setCount(1);
                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                contents.storedItemId(), newItems);
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(slotInfo.cabinetPos);
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(1);
                    return remaining;
                }
            }
        }
        return stack;
    }

    private ItemStack handleNBTFolderExtraction(VirtualSlotInfo slotInfo, int amount, boolean simulate) {
        if (level.getBlockEntity(slotInfo.cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotInfo.slotIndex);
            if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents != null && slotInfo.nbtIndex >= 0 && slotInfo.nbtIndex < contents.storedItems().size()) {
                    NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(slotInfo.nbtIndex);

                    if (!simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());
                        newItems.remove(slotInfo.nbtIndex);

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                contents.storedItemId(), newItems);
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                        notifyUpdate(slotInfo.cabinetPos);
                    }

                    return serializedItem.stack().copy();
                }
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
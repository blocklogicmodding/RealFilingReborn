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

    private List<VirtualSlotInfo> getAllVirtualSlots() {
        List<VirtualSlotInfo> virtualSlots = new ArrayList<>();

        for (BlockPos cabinetPos : indexEntity.getLinkedCabinets()) {
            // Check if cabinet is within range
            if (!isInRange(cabinetPos)) {
                continue;
            }

            // Handle Filing Cabinets
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
                            // Create virtual slots for each unique NBT item
                            for (int i = 0; i < contents.storedItems().size(); i++) {
                                NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(i);
                                virtualSlots.add(new VirtualSlotInfo(cabinetPos, slot, VirtualSlotType.NBT_FOLDER, serializedItem.stack().copy(), i));
                            }
                        }
                    }
                }
            }
            // Handle Fluid Cabinets - REMOVED, fluids handled by separate FluidHandler capability
        }

        return virtualSlots;
    }

    private boolean isInRange(BlockPos cabinetPos) {
        double distance = Math.sqrt(indexEntity.getBlockPos().distSqr(cabinetPos));
        return distance <= indexEntity.getRange();
    }

    @Override
    public int getSlots() {
        return getAllVirtualSlots().size();
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        List<VirtualSlotInfo> virtualSlots = getAllVirtualSlots();
        if (slot < 0 || slot >= virtualSlots.size()) {
            return ItemStack.EMPTY;
        }

        VirtualSlotInfo slotInfo = virtualSlots.get(slot);
        return slotInfo.virtualStack.copy();
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        List<VirtualSlotInfo> virtualSlots = getAllVirtualSlots();
        if (slot < 0 || slot >= virtualSlots.size() || stack.isEmpty()) {
            return stack;
        }

        VirtualSlotInfo slotInfo = virtualSlots.get(slot);

        // Only allow insertion if the item types match
        if (!ItemStack.isSameItem(stack, slotInfo.virtualStack)) {
            return stack;
        }

        if (slotInfo.type == VirtualSlotType.FILING_FOLDER) {
            return handleFilingFolderInsertion(slotInfo, stack, simulate);
        } else if (slotInfo.type == VirtualSlotType.NBT_FOLDER) {
            return handleNBTFolderInsertion(slotInfo, stack, simulate);
        }

        return stack;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        List<VirtualSlotInfo> virtualSlots = getAllVirtualSlots();
        if (slot < 0 || slot >= virtualSlots.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        VirtualSlotInfo slotInfo = virtualSlots.get(slot);

        if (slotInfo.type == VirtualSlotType.FILING_FOLDER) {
            return handleFilingFolderExtraction(slotInfo, amount, simulate);
        } else if (slotInfo.type == VirtualSlotType.NBT_FOLDER) {
            return handleNBTFolderExtraction(slotInfo, amount, simulate);
        }

        return ItemStack.EMPTY;
    }

    private ItemStack handleFilingFolderInsertion(VirtualSlotInfo slotInfo, ItemStack stack, boolean simulate) {
        if (level.getBlockEntity(slotInfo.cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slotInfo.slotIndex);
            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents != null && contents.storedItemId().isPresent()) {
                    int maxToAdd = Integer.MAX_VALUE - contents.count();
                    int toAdd = Math.min(stack.getCount(), maxToAdd);

                    if (toAdd > 0 && !simulate) {
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                contents.storedItemId(), contents.count() + toAdd);
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(toAdd);
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
                    }

                    return serializedItem.stack().copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        List<VirtualSlotInfo> virtualSlots = getAllVirtualSlots();
        if (slot < 0 || slot >= virtualSlots.size()) {
            return 0;
        }

        VirtualSlotInfo slotInfo = virtualSlots.get(slot);
        if (slotInfo.type == VirtualSlotType.NBT_FOLDER) {
            return 1; // NBT items are stored individually
        }

        return slotInfo.virtualStack.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        List<VirtualSlotInfo> virtualSlots = getAllVirtualSlots();
        if (slot < 0 || slot >= virtualSlots.size()) {
            return false;
        }

        VirtualSlotInfo slotInfo = virtualSlots.get(slot);
        return ItemStack.isSameItem(stack, slotInfo.virtualStack);
    }

    private static class VirtualSlotInfo {
        final BlockPos cabinetPos;
        final int slotIndex;
        final VirtualSlotType type;
        final ItemStack virtualStack;
        final int nbtIndex; // For NBT folders to track which specific item

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
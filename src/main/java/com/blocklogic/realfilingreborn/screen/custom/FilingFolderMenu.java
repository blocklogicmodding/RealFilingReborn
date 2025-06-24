package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FilingFolderMenu extends AbstractContainerMenu {
    private final ItemStackHandler assignmentInventory;
    private final ItemStack folderStack;
    private final int folderSlot;
    private final Inventory playerInventory;
    private final boolean isNBTFolder;

    public FilingFolderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readInt());
    }

    public FilingFolderMenu(int containerId, Inventory playerInventory, int folderSlot) {
        super(ModMenuTypes.FILING_FOLDER_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.folderSlot = folderSlot;
        this.folderStack = playerInventory.getItem(folderSlot);
        this.isNBTFolder = folderStack.getItem() instanceof NBTFilingFolderItem;

        this.assignmentInventory = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                updateFolderAssignment();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (isNBTFolder) {
                    return NBTFilingFolderItem.hasSignificantNBT(stack);
                } else {
                    return !FilingFolderItem.hasSignificantNBT(stack);
                }
            }
        };

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        this.addSlot(new SlotItemHandler(this.assignmentInventory, 0, 80, 43) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return assignmentInventory.isItemValid(0, stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                updateFolderAssignment();
            }
        });
    }

    private void updateFolderAssignment() {
        if (folderStack.isEmpty()) return;

        ItemStack assignedItem = assignmentInventory.getStackInSlot(0);

        if (assignedItem.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(assignedItem.getItem());

        if (isNBTFolder) {
            NBTFilingFolderItem.NBTFolderContents currentContents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

            if (currentContents == null || currentContents.storedItemId().isEmpty()) {
                List<NBTFilingFolderItem.SerializedItemStack> items = new ArrayList<>();
                ItemStack singleItem = assignedItem.copy();
                singleItem.setCount(1);
                items.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));

                NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                        Optional.of(itemId), items);
                folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);

                assignmentInventory.setStackInSlot(0, ItemStack.EMPTY);
            }
        } else {
            FilingFolderItem.FolderContents currentContents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

            if (currentContents == null || currentContents.storedItemId().isEmpty()) {
                FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                        Optional.of(itemId), assignedItem.getCount());
                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);

                assignmentInventory.setStackInSlot(0, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index == 36) {
                if (!this.moveItemStackTo(itemstack1, 0, 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (assignmentInventory.isItemValid(0, itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 36, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    public Component getCurrentCountText() {
        if (folderStack.isEmpty()) return null;

        if (isNBTFolder) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItemId().isPresent()) {
                int count = contents.storedItems().size();
                return Component.translatable("gui.realfilingreborn.current_nbt_count", count, NBTFilingFolderItem.MAX_NBT_ITEMS);
            }
        } else {
            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItemId().isPresent()) {
                return Component.translatable("gui.realfilingreborn.current_item_count", String.format("%,d", contents.count()));
            }
        }
        return null;
    }

    public void extractItems() {
        if (folderStack.isEmpty()) return;

        if (isNBTFolder) {
            extractFromNBTFolder();
        } else {
            extractFromRegularFolder();
        }
    }

    private void extractFromRegularFolder() {
        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
        if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
            return;
        }

        ResourceLocation itemId = contents.storedItemId().get();
        Item item = BuiltInRegistries.ITEM.get(itemId);

        ItemStack dummyStack = new ItemStack(item);
        int maxStackSize = item.getMaxStackSize(dummyStack);
        int extractAmount = Math.min(contents.count(), maxStackSize);

        if (extractAmount <= 0) return;

        ItemStack extractedStack = new ItemStack(item, extractAmount);

        Player player = playerInventory.player;
        if (player.getInventory().add(extractedStack)) {
            int newCount = contents.count() - extractAmount;
            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                    contents.storedItemId(),
                    Math.max(0, newCount)
            );
            folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
        } else {
            player.drop(extractedStack, false);

            int newCount = contents.count() - extractAmount;
            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                    contents.storedItemId(),
                    Math.max(0, newCount)
            );
            folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
        }

        this.broadcastChanges();
    }

    private void extractFromNBTFolder() {
        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
        if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
            return;
        }

        List<NBTFilingFolderItem.SerializedItemStack> items = new ArrayList<>(contents.storedItems());
        NBTFilingFolderItem.SerializedItemStack serializedItem = items.remove(items.size() - 1);
        ItemStack extracted = serializedItem.stack().copy();

        Player player = playerInventory.player;
        if (player.getInventory().add(extracted)) {
            NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                    contents.storedItemId(),
                    items
            );
            folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
        } else {
            player.drop(extracted, false);

            NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                    contents.storedItemId(),
                    items
            );
            folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return !folderStack.isEmpty() &&
                (folderStack.getItem() instanceof FilingFolderItem ||
                        folderStack.getItem() instanceof NBTFilingFolderItem);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!assignmentInventory.getStackInSlot(0).isEmpty()) {
            player.drop(assignmentInventory.getStackInSlot(0), false);
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 70 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 128));
        }
    }

    public boolean isNBTFolder() {
        return isNBTFolder;
    }
}
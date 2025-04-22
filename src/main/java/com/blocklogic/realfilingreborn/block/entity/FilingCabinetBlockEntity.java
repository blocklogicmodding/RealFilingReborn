package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FilingCabinetBlockEntity extends BlockEntity implements MenuProvider {
    private BlockPos previousIndexPos = null;

    // Update to 13 slots: 12 for folders (0-11) and 1 for index card (12)
    public final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == 12) { // Updated index card slot number
                updateIndexLinking();
            }
            if(!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                level.invalidateCapabilities(getBlockPos());
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 12) { // Updated number of folder slots
                return stack.getItem() instanceof FilingFolderItem || stack.getItem() instanceof NBTFilingFolderItem;
            } else {
                // This is the index card slot
                return stack.getItem() instanceof IndexCardItem &&
                        stack.get(ModDataComponents.COORDINATES) != null;
            }
        }
    };

    private final Map<Direction, IItemHandler> handlers = new HashMap<>();
    private final IItemHandler nullSideHandler = new FilingCabinetItemHandler(this, null);

    public FilingCabinetBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_CABINET_BE.get(), pos, blockState);
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        if (side != null && this.getBlockState().getValue(FilingCabinetBlock.FACING) == side) {
            return null;
        }

        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingCabinetItemHandler(this, s));
    }

    private BlockPos getLinkedIndexPos() {
        ItemStack indexCardStack = inventory.getStackInSlot(12); // Updated index card slot number
        if (!indexCardStack.isEmpty() && indexCardStack.get(ModDataComponents.COORDINATES) != null) {
            return indexCardStack.get(ModDataComponents.COORDINATES);
        }
        return null;
    }

    public void updateIndexLinking() {
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(getBlockPos());
        }
    }

    public void clearContents() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        // Save the previous index position
        if (previousIndexPos != null) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", previousIndexPos.getX());
            posTag.putInt("Y", previousIndexPos.getY());
            posTag.putInt("Z", previousIndexPos.getZ());
            tag.put("PreviousIndexPos", posTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        if (tag.contains("PreviousIndexPos", Tag.TAG_COMPOUND)) {
            CompoundTag posTag = tag.getCompound("PreviousIndexPos");
            previousIndexPos = new BlockPos(
                    posTag.getInt("X"),
                    posTag.getInt("Y"),
                    posTag.getInt("Z")
            );
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("blockentity.realfilingreborn.name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new FilingCabinetMenu(i, inventory, this);
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

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateIndexLinking();
    }

    private static class FilingCabinetItemHandler implements IItemHandler {
        private final FilingCabinetBlockEntity cabinet;
        private final Direction side;

        public FilingCabinetItemHandler(FilingCabinetBlockEntity cabinet, @Nullable Direction side) {
            this.cabinet = cabinet;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 12; // Updated number of folder slots
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getSlots()) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

            // Handle regular filing folders
            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
                    return ItemStack.EMPTY;
                }

                ResourceLocation itemId = contents.storedItemId().get();
                Item item = BuiltInRegistries.ITEM.get(itemId);

                // Return the full item count
                return new ItemStack(item, contents.count());
            }
            // Handle NBT filing folders
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
                    return ItemStack.EMPTY;
                }

                // For NBT folders, let's provide the first stored item
                if (!contents.storedItems().isEmpty()) {
                    NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(0);
                    ItemStack firstItem = serializedItem.stack().copy();
                    firstItem.setCount(contents.storedItems().size()); // Set count to total number of items
                    return firstItem;
                }
            }

            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // Reject empty stacks, invalid slots, or if the slot doesn't exist
            if (slot < 0 || slot >= getSlots() || stack.isEmpty()) {
                return stack;
            }

            // When coming from automation (side is not null), we need to find a folder that matches this item type
            if (side != null) {
                ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                // First check regular filing folders
                for (int i = 0; i < 12; i++) {
                    ItemStack folderStack = cabinet.inventory.getStackInSlot(i);

                    // Skip empty slots and non-folder items
                    if (folderStack.isEmpty()) {
                        continue;
                    }

                    // Handle regular filing folders
                    if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

                        // If folder is empty/uninitialized, skip it
                        if (contents == null || contents.storedItemId().isEmpty()) {
                            continue;
                        }

                        ResourceLocation folderItemId = contents.storedItemId().get();

                        // Check if this folder is registered for the item we're trying to insert
                        if (folderItemId.equals(stackItemId)) {
                            // We found a matching folder! Now we can insert the items.
                            int maxToAdd = Integer.MAX_VALUE - contents.count();
                            int toAdd = Math.min(stack.getCount(), maxToAdd);

                            // If folder is full, try the next folder
                            if (toAdd <= 0) {
                                continue;
                            }

                            // Update the folder contents if not simulating
                            if (!simulate) {
                                FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                        contents.storedItemId(),
                                        contents.count() + toAdd
                                );
                                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                                cabinet.setChanged();
                            }

                            // Return the remaining items that didn't fit
                            ItemStack remaining = stack.copy();
                            remaining.shrink(toAdd);
                            return remaining;
                        }
                    }
                }

                // Then check NBT filing folders if regular folders didn't work
                for (int i = 0; i < 12; i++) {
                    ItemStack folderStack = cabinet.inventory.getStackInSlot(i);

                    // Skip empty slots
                    if (folderStack.isEmpty()) {
                        continue;
                    }

                    // Check if it's an NBT filing folder
                    if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

                        // If folder is empty/uninitialized, skip it
                        if (contents == null || contents.storedItemId().isEmpty()) {
                            continue;
                        }

                        ResourceLocation folderItemId = contents.storedItemId().get();

                        // Check if this folder is registered for the item we're trying to insert
                        if (folderItemId.equals(stackItemId)) {
                            // Check if the item has significant NBT (this is the key part for NBT folders)
                            // Use the same logic that NBTFilingFolderItem uses
                            boolean hasNBT = hasSignificantNBT(stack);

                            if (hasNBT) {
                                // We found a matching NBT folder and the item has NBT!

                                if (!simulate) {
                                    // Get the current items
                                    List<NBTFilingFolderItem.SerializedItemStack> newItems =
                                            new ArrayList<>(contents.storedItems() != null ? contents.storedItems() : new ArrayList<>());

                                    // Add each item from the stack individually (since each may have unique NBT)
                                    for (int count = 0; count < stack.getCount(); count++) {
                                        ItemStack singleItem = stack.copy();
                                        singleItem.setCount(1);
                                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                                    }

                                    // Update the folder contents
                                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                            contents.storedItemId(),
                                            newItems
                                    );
                                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                                    cabinet.setChanged();
                                }

                                // All items consumed
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }

                // If we got here, it means no matching folder was found.
                // For automation, we reject the entire stack.
                return stack;
            }

            // The rest of the code for GUI/direct player interaction remains the same
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

            // The slot doesn't have a folder or is empty
            if (folderStack.isEmpty()) {
                return stack;
            }

            // Handle regular filing folders
            if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null) {
                    contents = new FilingFolderItem.FolderContents(Optional.empty(), 0);
                }

                // Regular folder handling...
                // If folder isn't registered for any item yet, register it for this item
                if (contents.storedItemId().isEmpty()) {
                    ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                    if (!simulate) {
                        int toAdd = Math.min(stack.getCount(), Integer.MAX_VALUE);
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                Optional.of(newItemId),
                                toAdd
                        );
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                    }

                    ItemStack remaining = stack.copy();
                    remaining.shrink(stack.getCount());
                    return remaining;
                }

                // Folder is registered, check if it's registered for the right item
                ResourceLocation itemId = contents.storedItemId().get();
                ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                if (!itemId.equals(stackItemId)) {
                    // Wrong item type, reject
                    return stack;
                }

                // Calculate how many items we can add
                int maxToAdd = Integer.MAX_VALUE - contents.count();
                int toAdd = Math.min(stack.getCount(), maxToAdd);

                if (toAdd <= 0) {
                    // Folder is full
                    return stack;
                }

                // Update the folder contents if not simulating
                if (!simulate) {
                    FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                            contents.storedItemId(),
                            contents.count() + toAdd
                    );
                    folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                    cabinet.setChanged();
                }

                // Return the remaining items that didn't fit
                ItemStack remaining = stack.copy();
                remaining.shrink(toAdd);
                return remaining;
            }
            // Handle NBT filing folders
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null) {
                    contents = new NBTFilingFolderItem.NBTFolderContents(Optional.empty(), new ArrayList<>());
                }

                // If folder isn't registered for any item yet, register it for this item if it has NBT
                if (contents.storedItemId().isEmpty()) {
                    // Check if the item has significant NBT
                    if (!hasSignificantNBT(stack)) {
                        // Item doesn't have NBT, reject
                        return stack;
                    }

                    ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                    if (!simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>();

                        // Add each item from the stack individually
                        for (int count = 0; count < stack.getCount(); count++) {
                            ItemStack singleItem = stack.copy();
                            singleItem.setCount(1);
                            newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                        }

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                Optional.of(newItemId),
                                newItems
                        );
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.setChanged();
                    }

                    // All items consumed
                    return ItemStack.EMPTY;
                }

                // Folder is registered, check if it's registered for the right item
                ResourceLocation itemId = contents.storedItemId().get();
                ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                if (!itemId.equals(stackItemId)) {
                    // Wrong item type, reject
                    return stack;
                }

                // Check if the item has significant NBT
                if (!hasSignificantNBT(stack)) {
                    // Item doesn't have NBT, reject
                    return stack;
                }

                // Item has NBT and matches the folder's item type
                if (!simulate) {
                    List<NBTFilingFolderItem.SerializedItemStack> newItems =
                            new ArrayList<>(contents.storedItems() != null ? contents.storedItems() : new ArrayList<>());

                    // Add each item from the stack individually
                    for (int count = 0; count < stack.getCount(); count++) {
                        ItemStack singleItem = stack.copy();
                        singleItem.setCount(1);
                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                    }

                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                            contents.storedItemId(),
                            newItems
                    );
                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                    cabinet.setChanged();
                }

                // All items consumed
                return ItemStack.EMPTY;
            }

            // If we got here, it means the slot doesn't contain a valid folder
            return stack;
        }

        // Helper method to check if an item has significant NBT data (copied from NBTFilingFolderItem)
        private boolean hasSignificantNBT(ItemStack stack) {
            if (stack.isEmpty()) return false;

            // Check for damage
            if (stack.isDamaged()) return true;

            // Check for enchantments
            if (stack.get(DataComponents.ENCHANTMENTS) != null &&
                    !stack.get(DataComponents.ENCHANTMENTS).isEmpty()) return true;

            // Check for custom name
            if (stack.get(DataComponents.CUSTOM_NAME) != null) return true;

            // Check for lore
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                return true;
            }

            // Other checks for significant data components could be added here

            return false;
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

            // Handle regular filing folders
            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
                    return ItemStack.EMPTY;
                }

                ResourceLocation itemId = contents.storedItemId().get();
                Item item = BuiltInRegistries.ITEM.get(itemId);

                ItemStack result = new ItemStack(item, 1);
                int maxStackSize = item.getMaxStackSize(result);
                int extractAmount = Math.min(Math.min(contents.count(), amount), maxStackSize);

                if (extractAmount <= 0) {
                    return ItemStack.EMPTY;
                }

                result.setCount(extractAmount);

                if (!simulate) {
                    int newCount = contents.count() - extractAmount;
                    FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                            contents.storedItemId(),
                            newCount
                    );
                    folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                    cabinet.setChanged();
                }

                return result;
            }
            // Handle NBT filing folders
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
                    return ItemStack.EMPTY;
                }

                // Extract the last item for NBT folders (LIFO order)
                List<NBTFilingFolderItem.SerializedItemStack> items = new ArrayList<>(contents.storedItems());
                int extractAmount = Math.min(amount, items.size());

                if (extractAmount <= 0) {
                    return ItemStack.EMPTY;
                }

                // Get the first item to determine type
                NBTFilingFolderItem.SerializedItemStack serializedItem = items.get(items.size() - 1);
                ItemStack extracted = serializedItem.stack().copy();

                // For non-stackable or NBT items, only extract one at a time
                int actualExtract = extracted.isStackable() ? extractAmount : 1;
                extracted.setCount(actualExtract);

                if (!simulate) {
                    // Remove the extracted items from the end of the list
                    for (int i = 0; i < actualExtract; i++) {
                        if (!items.isEmpty()) {
                            items.remove(items.size() - 1);
                        }
                    }

                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                            contents.storedItemId(),
                            items
                    );
                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                    cabinet.setChanged();
                }

                return extracted;
            }

            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 12) { // Updated number of folder slots
                return stack.getItem() instanceof FilingFolderItem;
            } else {
                // This is the index card slot
                return stack.getItem() instanceof IndexCardItem &&
                        stack.get(ModDataComponents.COORDINATES) != null;
            }
        }
    }
}
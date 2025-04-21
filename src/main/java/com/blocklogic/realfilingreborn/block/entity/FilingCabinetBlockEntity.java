package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FilingCabinetBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler inventory = new ItemStackHandler(11) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == 10) { // Index card slot
                updateIndexLinking();
            }
            if(!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                level.invalidateCapabilities(getBlockPos());
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 10) {
                return stack.getItem() instanceof FilingFolderItem;
            } else {
                return stack.getItem() instanceof IndexCardItem;
            }
        }
    };

    private final Map<Direction, IItemHandler> handlers = new HashMap<>();
    private final IItemHandler nullSideHandler = new FilingCabinetItemHandler(this, null);

    public FilingCabinetBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_CABINET_BE.get(), pos, blockState);
    }

    /**
     * Get the capability handler for the specified side
     * @param side The side to get the handler for, or null for no specific side
     * @return The appropriate item handler or null if no handler exists for that side
     */
    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        if (side != null && this.getBlockState().getValue(FilingCabinetBlock.FACING) == side) {
            return null;
        }

        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingCabinetItemHandler(this, s));
    }

    private BlockPos getLinkedIndexPos() {
        ItemStack indexCardStack = inventory.getStackInSlot(10);
        if (!indexCardStack.isEmpty() && indexCardStack.get(ModDataComponents.COORDINATES) != null) {
            return indexCardStack.get(ModDataComponents.COORDINATES);
        }
        return null;
    }

    public void updateIndexLinking() {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos indexPos = getLinkedIndexPos();
        if (indexPos != null) {
            // Check if the index block is still a valid Filing Index
            if (level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity indexEntity) {
                indexEntity.addCabinet(this.getBlockPos());
            }
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
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
        if (level != null && !level.isClientSide()) {
            BlockPos indexPos = getLinkedIndexPos();
            if (indexPos != null) {
                if (level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity indexEntity) {
                    indexEntity.removeCabinet(this.getBlockPos());
                }
            }
        }
        super.setRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateIndexLinking();
    }

    /**
     * Item handler implementation for filing cabinet automation
     * This provides a view of the folder contents rather than the folders themselves
     */
    private static class FilingCabinetItemHandler implements IItemHandler {
        private final FilingCabinetBlockEntity cabinet;
        private final Direction side;

        public FilingCabinetItemHandler(FilingCabinetBlockEntity cabinet, @Nullable Direction side) {
            this.cabinet = cabinet;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 10;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getSlots()) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (folderStack.isEmpty() || !(folderStack.getItem() instanceof FilingFolderItem)) {
                return ItemStack.EMPTY;
            }

            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
                return ItemStack.EMPTY;
            }

            ResourceLocation itemId = contents.storedItemId().get();
            Item item = BuiltInRegistries.ITEM.get(itemId);

            ItemStack tempStack = new ItemStack(item, 1);
            int maxStackSize = item.getMaxStackSize(tempStack);
            int stackSize = Math.min(contents.count(), maxStackSize);

            return new ItemStack(item, stackSize);
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || stack.isEmpty()) {
                return stack;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (folderStack.isEmpty() || !(folderStack.getItem() instanceof FilingFolderItem)) {
                return stack;
            }

            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new FilingFolderItem.FolderContents(Optional.empty(), 0);
            }

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

            ResourceLocation itemId = contents.storedItemId().get();
            ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

            if (!itemId.equals(stackItemId)) {
                return stack;
            }

            int maxToAdd = Integer.MAX_VALUE - contents.count();
            int toAdd = Math.min(stack.getCount(), maxToAdd);

            if (toAdd <= 0) {
                return stack;
            }

            if (!simulate) {
                FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                        contents.storedItemId(),
                        contents.count() + toAdd
                );
                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                cabinet.setChanged();
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(toAdd);
            return remaining;
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (folderStack.isEmpty() || !(folderStack.getItem() instanceof FilingFolderItem)) {
                return ItemStack.EMPTY;
            }

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

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < 0 || slot >= getSlots() || stack.isEmpty()) {
                return false;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (folderStack.isEmpty() || !(folderStack.getItem() instanceof FilingFolderItem)) {
                return false;
            }

            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents == null || contents.storedItemId().isEmpty()) {
                return true;
            }

            ResourceLocation itemId = contents.storedItemId().get();
            ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId.equals(stackItemId);
        }
    }
}
package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.config.Config;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FilingCabinetBlockEntity extends BlockEntity implements MenuProvider {

    @Nullable
    private BlockPos controllerPos = null;

    public final ItemStackHandler inventory = new ItemStackHandler(5) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final Map<Direction, IItemHandler> handlers = new HashMap<>();

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

        if (controllerPos != null) {
            tag.putLong("controllerPos", controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        if (tag.contains("controllerPos")) {
            controllerPos = BlockPos.of(tag.getLong("controllerPos"));
        } else {
            controllerPos = null;
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

    private void notifyFolderContentsChanged() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            setChanged();
        }
    }

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void clearControllerPos() {
        this.controllerPos = null;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isLinkedToController() {
        return controllerPos != null;
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
            return 5;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getSlots()) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

            if (folderStack.getItem() instanceof FilingFolderItem) {
                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
                    return ItemStack.EMPTY;
                }

                ResourceLocation itemId = contents.storedItemId().get();
                Item item = BuiltInRegistries.ITEM.get(itemId);

                return new ItemStack(item, contents.count());
            }
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
                    return ItemStack.EMPTY;
                }

                if (!contents.storedItems().isEmpty()) {
                    NBTFilingFolderItem.SerializedItemStack serializedItem = contents.storedItems().get(0);
                    ItemStack firstItem = serializedItem.stack().copy();
                    firstItem.setCount(contents.storedItems().size());
                    return firstItem;
                }
            }

            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || stack.isEmpty()) {
                return stack;
            }

            if (side != null) {
                ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                for (int i = 0; i < 5; i++) {
                    ItemStack folderStack = cabinet.inventory.getStackInSlot(i);

                    if (folderStack.isEmpty()) {
                        continue;
                    }

                    if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

                        if (contents == null || contents.storedItemId().isEmpty()) {
                            continue;
                        }

                        ResourceLocation folderItemId = contents.storedItemId().get();

                        if (folderItemId.equals(stackItemId)) {
                            if (FilingFolderItem.hasSignificantNBT(stack)) {
                                continue;
                            }

                            int maxToAdd = Config.getMaxFolderStorage() - contents.count();
                            int toAdd = Math.min(stack.getCount(), maxToAdd);

                            if (toAdd <= 0) {
                                continue;
                            }

                            if (!simulate) {
                                FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                        contents.storedItemId(),
                                        contents.count() + toAdd
                                );
                                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                                cabinet.notifyFolderContentsChanged();
                            }

                            ItemStack remaining = stack.copy();
                            remaining.shrink(toAdd);
                            return remaining;
                        }
                    }
                }

                for (int i = 0; i < 5; i++) {
                    ItemStack folderStack = cabinet.inventory.getStackInSlot(i);

                    if (folderStack.isEmpty()) {
                        continue;
                    }

                    if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

                        if (contents == null || contents.storedItemId().isEmpty()) {
                            continue;
                        }

                        ResourceLocation folderItemId = contents.storedItemId().get();

                        if (folderItemId.equals(stackItemId)) {
                            boolean hasNBT = hasSignificantNBT(stack);

                            if (hasNBT) {
                                if (contents.storedItems() != null &&
                                        contents.storedItems().size() >= Config.getMaxNBTFolderStorage()) {
                                    return stack;
                                }

                                if (!simulate) {
                                    List<NBTFilingFolderItem.SerializedItemStack> newItems =
                                            new ArrayList<>(contents.storedItems() != null ? contents.storedItems() : new ArrayList<>());

                                    int availableSpace = Config.getMaxNBTFolderStorage() - newItems.size();
                                    int itemsToAdd = Math.min(stack.getCount(), availableSpace);

                                    for (int count = 0; count < itemsToAdd; count++) {
                                        ItemStack singleItem = stack.copy();
                                        singleItem.setCount(1);
                                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                                    }

                                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                            contents.storedItemId(),
                                            newItems
                                    );
                                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                                    cabinet.notifyFolderContentsChanged();

                                    if (itemsToAdd < stack.getCount()) {
                                        ItemStack remaining = stack.copy();
                                        remaining.setCount(stack.getCount() - itemsToAdd);
                                        return remaining;
                                    }
                                }

                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }

                return stack;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

            if (folderStack.isEmpty()) {
                return stack;
            }

            if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                if (FilingFolderItem.hasSignificantNBT(stack)) {
                    return stack;
                }

                FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                if (contents == null) {
                    contents = new FilingFolderItem.FolderContents(Optional.empty(), 0);
                }

                if (contents.storedItemId().isEmpty()) {
                    ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                    if (!simulate) {
                        int toAdd = Math.min(stack.getCount(), Config.getMaxFolderStorage());
                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                Optional.of(newItemId),
                                toAdd
                        );
                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                        cabinet.notifyFolderContentsChanged();
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

                int maxToAdd;
                if (contents.count() > Config.getMaxFolderStorage() - 1000) {
                    return stack;
                }
                maxToAdd = Config.getMaxFolderStorage() - contents.count();
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
                    cabinet.notifyFolderContentsChanged();
                }

                ItemStack remaining = stack.copy();
                remaining.shrink(toAdd);
                return remaining;
            }
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null) {
                    contents = new NBTFilingFolderItem.NBTFolderContents(Optional.empty(), new ArrayList<>());
                }

                if (contents.storedItems() != null &&
                        contents.storedItems().size() >= Config.getMaxNBTFolderStorage()) {
                    return stack;
                }

                if (contents.storedItemId().isEmpty()) {
                    if (!hasSignificantNBT(stack)) {
                        return stack;
                    }

                    ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                    if (!simulate) {
                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>();

                        int availableSpace = Config.getMaxNBTFolderStorage();
                        int itemsToAdd = Math.min(stack.getCount(), availableSpace);

                        for (int count = 0; count < itemsToAdd; count++) {
                            ItemStack singleItem = stack.copy();
                            singleItem.setCount(1);
                            newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                        }

                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                Optional.of(newItemId),
                                newItems
                        );
                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                        cabinet.notifyFolderContentsChanged();

                        if (itemsToAdd < stack.getCount()) {
                            ItemStack remaining = stack.copy();
                            remaining.setCount(stack.getCount() - itemsToAdd);
                            return remaining;
                        }
                    }

                    return ItemStack.EMPTY;
                }

                ResourceLocation itemId = contents.storedItemId().get();
                ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                if (!itemId.equals(stackItemId)) {
                    return stack;
                }

                if (!hasSignificantNBT(stack)) {
                    return stack;
                }

                if (!simulate) {
                    List<NBTFilingFolderItem.SerializedItemStack> newItems =
                            new ArrayList<>(contents.storedItems() != null ? contents.storedItems() : new ArrayList<>());

                    int availableSpace = Config.getMaxNBTFolderStorage() - newItems.size();
                    int itemsToAdd = Math.min(stack.getCount(), availableSpace);

                    for (int count = 0; count < itemsToAdd; count++) {
                        ItemStack singleItem = stack.copy();
                        singleItem.setCount(1);
                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                    }

                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                            contents.storedItemId(),
                            newItems
                    );
                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                    cabinet.notifyFolderContentsChanged();

                    if (itemsToAdd < stack.getCount()) {
                        ItemStack remaining = stack.copy();
                        remaining.setCount(stack.getCount() - itemsToAdd);
                        return remaining;
                    }
                }

                return ItemStack.EMPTY;
            }

            return stack;
        }

        private boolean hasSignificantNBT(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (stack.isDamaged()) return true;

            ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) return true;

            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) return true;

            if (stack.get(DataComponents.CUSTOM_NAME) != null) return true;

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) return true;

            PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
            if (potion != null && (!potion.customEffects().isEmpty() || !potion.potion().isEmpty())) return true;

            return false;
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

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
                    cabinet.notifyFolderContentsChanged();
                }

                return result;
            }
            else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
                    return ItemStack.EMPTY;
                }

                List<NBTFilingFolderItem.SerializedItemStack> items = new ArrayList<>(contents.storedItems());
                int extractAmount = Math.min(amount, items.size());

                if (extractAmount <= 0) {
                    return ItemStack.EMPTY;
                }

                NBTFilingFolderItem.SerializedItemStack serializedItem = items.get(items.size() - 1);
                ItemStack extracted = serializedItem.stack().copy();

                int actualExtract = extracted.isStackable() ? extractAmount : 1;
                extracted.setCount(actualExtract);

                if (!simulate) {
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
                    cabinet.notifyFolderContentsChanged();
                }

                return extracted;
            }

            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
            if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                return Config.getMaxNBTFolderStorage();
            }
            return Config.getMaxFolderStorage();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof FilingFolderItem;
        }
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
}
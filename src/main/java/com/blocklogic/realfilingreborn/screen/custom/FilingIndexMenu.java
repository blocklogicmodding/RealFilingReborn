package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.minecraft.world.SimpleContainer;

public class FilingIndexMenu extends AbstractContainerMenu {
    public final FilingIndexBlockEntity blockEntity;
    private final Level level;
    private final IItemHandler networkItemHandler;

    // Slot counts based on GUI specification
    private static final int STORAGE_ROWS = 8;
    private static final int STORAGE_COLS = 12;
    private static final int STORAGE_SLOTS = STORAGE_ROWS * STORAGE_COLS; // 96 slots

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int STORAGE_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    public FilingIndexMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public FilingIndexMenu(int containerId, Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.FILING_INDEX_MENU.get(), containerId);
        this.blockEntity = ((FilingIndexBlockEntity) blockEntity);
        this.level = inv.player.level();
        this.networkItemHandler = this.blockEntity.getNetworkItemHandler();

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
        addStorageSlots();
    }

    private void addStorageSlots() {
        // Storage grid: 12x8 starting at x12, y21
        int startX = 12;
        int startY = 21;
        int slotIndex = 0;

        for (int row = 0; row < STORAGE_ROWS; row++) {
            for (int col = 0; col < STORAGE_COLS; col++) {
                int x = startX + (col * 18);
                int y = startY + (row * 18);

                if (slotIndex < networkItemHandler.getSlots()) {
                    this.addSlot(new SlotItemHandler(networkItemHandler, slotIndex, x, y));
                } else {
                    // Add empty slots if network doesn't have enough items
                    this.addSlot(new EmptyNetworkSlot(x, y));
                }
                slotIndex++;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Player inventory to storage
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, STORAGE_FIRST_SLOT_INDEX,
                    STORAGE_FIRST_SLOT_INDEX + STORAGE_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        // Storage to player inventory
        else if (pIndex < STORAGE_FIRST_SLOT_INDEX + STORAGE_SLOTS) {
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX,
                    VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + pIndex);
            return ItemStack.EMPTY;
        }

        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player, ModBlocks.FILING_INDEX.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // Player inventory at x48, y172
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 48 + l * 18, 172 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        // Player hotbar at x48, y230
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 48 + i * 18, 230));
        }
    }

    // Empty slot for when network has fewer items than display slots
    private static class EmptyNetworkSlot extends Slot {
        private static final SimpleContainer DUMMY_CONTAINER = new SimpleContainer(1);

        public EmptyNetworkSlot(int x, int y) {
            super(DUMMY_CONTAINER, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public ItemStack getItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            // Do nothing
        }

        @Override
        public void setChanged() {
            // Do nothing
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean hasItem() {
            return false;
        }
    }
}
package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.CapacityUpgradeItem;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class FilingCabinetMenu extends AbstractContainerMenu {
    public final FilingCabinetBlockEntity blockEntity;
    private final Level level;

    public FilingCabinetMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public FilingCabinetMenu(int containerId, Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.FILING_CABINET_MENU.get(), containerId);
        this.blockEntity = ((FilingCabinetBlockEntity) blockEntity);
        this.level = inv.player.level();

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                int x = 8 + col * 18;
                int y = 22 + row * 18;
                int slotIndex = col + row * 6;
                this.addSlot(new SlotItemHandler(this.blockEntity.inventory, slotIndex, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof FilingFolderItem || stack.getItem() instanceof NBTFilingFolderItem;
                    }
                });
            }
        }

        this.addSlot(new SlotItemHandler(this.blockEntity.inventory, 12, 152, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof IndexCardItem &&
                        stack.get(ModDataComponents.COORDINATES) != null;
            }
        });
    }

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    private static final int TE_INVENTORY_SLOT_COUNT = 13;

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            if (sourceStack.getItem() instanceof FilingFolderItem || sourceStack.getItem() instanceof NBTFilingFolderItem) {
                if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX,
                        TE_INVENTORY_FIRST_SLOT_INDEX + 12, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (sourceStack.getItem() instanceof IndexCardItem) {
                // Check if the card is linked
                if (sourceStack.get(ModDataComponents.COORDINATES) == null) {
                    return ItemStack.EMPTY;
                }

                // Check if linked filing index is at capacity
                BlockPos indexPos = sourceStack.get(ModDataComponents.COORDINATES);
                Level level = playerIn.level();
                if (level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity indexBE) {
                    // If index is at capacity, don't allow shift-clicking
                    if (!indexBE.canAcceptMoreCabinets()) {
                        if (!level.isClientSide()) {
                            boolean hasUpgrade = !indexBE.inventory.getStackInSlot(0).isEmpty();
                            if (hasUpgrade) {
                                playerIn.displayClientMessage(
                                        Component.translatable("message.realfilingreborn.index_at_max_capacity")
                                                .withStyle(ChatFormatting.RED),
                                        true);
                            } else {
                                playerIn.displayClientMessage(
                                        Component.translatable("message.realfilingreborn.index_at_base_capacity")
                                                .withStyle(ChatFormatting.RED),
                                        true);
                            }
                        }
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Index doesn't exist anymore
                    if (!level.isClientSide()) {
                        playerIn.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_no_longer_exists")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return ItemStack.EMPTY;
                }

                // If we get here, capacity is fine, so try to move the item
                if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX + 12,
                        TE_INVENTORY_FIRST_SLOT_INDEX + 13, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
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
                player, ModBlocks.FILING_CABINET.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
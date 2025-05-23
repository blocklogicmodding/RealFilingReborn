package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<FilingIndexBlock> CODEC = simpleCodec(FilingIndexBlock::new);

    private static final java.util.Map<java.util.UUID, Long> lastClickTime = new java.util.HashMap<>();
    private static final long DOUBLE_CLICK_THRESHOLD = 500;

    public FilingIndexBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(CONNECTED, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new FilingIndexBlockEntity(blockPos, blockState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity)  {
                filingIndexBlockEntity.disconnectAllCabinets();
                filingIndexBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void openFilingIndexMenu(FilingIndexBlockEntity blockEntity, ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, playerEntity) -> new FilingIndexMenu(id, inventory, blockEntity),
                Component.translatable("menu.realfilingreborn.filing_index_menu_title")
        ), pos);
    }

    public void updateConnectionState(Level level, BlockPos pos, boolean connected) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.getValue(CONNECTED) != connected) {
            level.setBlock(pos, currentState.setValue(CONNECTED, connected), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity)) {
            return ItemInteractionResult.FAIL;
        }

        if (player.isCrouching()) {
            if (!level.isClientSide()) {
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
                openFilingIndexMenu(filingIndexBlockEntity, (ServerPlayer) player, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.getItem() instanceof IndexRangeUpgradeIronItem
                || heldItem.getItem() instanceof IndexRangeUpgradeDiamondItem
                || heldItem.getItem() instanceof IndexRangeUpgradeNetheriteItem) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }

            int slot = 0;
            if (filingIndexBlockEntity.inventory.getStackInSlot(slot).isEmpty()) {
                ItemStack upgradeStack = heldItem.copyWithCount(1);
                filingIndexBlockEntity.inventory.setStackInSlot(slot, upgradeStack);
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                filingIndexBlockEntity.setChanged();
            } else {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.index_full"), true);
            }

            return ItemInteractionResult.SUCCESS;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        long currentTime = System.currentTimeMillis();
        java.util.UUID playerId = player.getUUID();
        Long lastClick = lastClickTime.get(playerId);
        boolean isDoubleClick = lastClick != null && (currentTime - lastClick) <= DOUBLE_CLICK_THRESHOLD;
        lastClickTime.put(playerId, currentTime);

        if (isDoubleClick) {
            return handleInventoryDump(filingIndexBlockEntity, player, level, pos);
        } else if (!heldItem.isEmpty() && !(heldItem.getItem() instanceof FilingFolderItem) && !(heldItem.getItem() instanceof NBTFilingFolderItem)) {
            return handleSingleItemInsertion(filingIndexBlockEntity, heldItem, player, level, pos);
        }

        return ItemInteractionResult.FAIL;
    }

    private ItemInteractionResult handleInventoryDump(FilingIndexBlockEntity indexEntity, Player player, Level level, BlockPos pos) {
        int totalItemsInserted = 0;

        for (int invSlot = 0; invSlot < player.getInventory().getContainerSize(); invSlot++) {
            ItemStack invStack = player.getInventory().getItem(invSlot);

            if (invStack.isEmpty() ||
                    invStack.getItem() instanceof FilingFolderItem ||
                    invStack.getItem() instanceof NBTFilingFolderItem) {
                continue;
            }

            int originalCount = invStack.getCount();
            insertItemIntoNetwork(indexEntity, invStack, level);
            int inserted = originalCount - invStack.getCount();

            if (inserted > 0) {
                totalItemsInserted += inserted;
                player.getInventory().setItem(invSlot, invStack.isEmpty() ? ItemStack.EMPTY : invStack);
            }
        }

        if (totalItemsInserted > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.2f);
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.items_inserted", totalItemsInserted),
                    true
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.no_compatible_folder"),
                    true
            );
        }

        return ItemInteractionResult.SUCCESS;
    }

    private ItemInteractionResult handleSingleItemInsertion(FilingIndexBlockEntity indexEntity, ItemStack heldItem, Player player, Level level, BlockPos pos) {
        int originalCount = heldItem.getCount();
        insertItemIntoNetwork(indexEntity, heldItem, level);
        int inserted = originalCount - heldItem.getCount();

        if (inserted > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
        } else {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.no_compatible_folder"), true);
        }

        return ItemInteractionResult.SUCCESS;
    }

    private void insertItemIntoNetwork(FilingIndexBlockEntity indexEntity, ItemStack itemStack, Level level) {
        if (itemStack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        boolean hasNbt = NBTFilingFolderItem.hasSignificantNBT(itemStack);

        for (FilingCabinetBlockEntity cabinet : indexEntity.getConnectedCabinetEntities()) {
            if (itemStack.isEmpty()) break;

            for (int slot = 0; slot < 5; slot++) {
                if (itemStack.isEmpty()) break;

                ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);

                if (folderStack.isEmpty()) {
                    continue;
                }

                if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                    if (hasNbt) continue;

                    FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                    if (contents == null) continue;

                    if (contents.storedItemId().isPresent() && contents.storedItemId().get().equals(itemId)) {
                        int maxToAdd = Integer.MAX_VALUE - contents.count();
                        int toAdd = Math.min(itemStack.getCount(), maxToAdd);

                        if (toAdd > 0) {
                            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                    contents.storedItemId(),
                                    contents.count() + toAdd
                            );
                            folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                            itemStack.shrink(toAdd);

                            level.sendBlockUpdated(cabinet.getBlockPos(), cabinet.getBlockState(), cabinet.getBlockState(), Block.UPDATE_CLIENTS);
                            cabinet.setChanged();
                        }
                    }
                }
                else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                    if (!hasNbt) continue;

                    NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                    if (contents == null) continue;

                    if (contents.storedItemId().isPresent() && contents.storedItemId().get().equals(itemId)) {
                        if (contents.storedItems().size() < NBTFilingFolderItem.MAX_NBT_ITEMS) {
                            List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());

                            int availableSpace = NBTFilingFolderItem.MAX_NBT_ITEMS - newItems.size();
                            int toAdd = Math.min(itemStack.getCount(), availableSpace);

                            for (int count = 0; count < toAdd; count++) {
                                ItemStack singleItem = itemStack.copy();
                                singleItem.setCount(1);
                                newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                            }

                            NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                    contents.storedItemId(),
                                    newItems
                            );
                            folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                            itemStack.shrink(toAdd);

                            level.sendBlockUpdated(cabinet.getBlockPos(), cabinet.getBlockState(), cabinet.getBlockState(), Block.UPDATE_CLIENTS);
                            cabinet.setChanged();
                        }
                    }
                }
            }
        }
    }
}
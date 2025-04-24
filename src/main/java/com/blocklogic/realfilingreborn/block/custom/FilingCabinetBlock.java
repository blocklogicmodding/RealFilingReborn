package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.RangeUpgradeTierOne;
import com.blocklogic.realfilingreborn.item.custom.RangeUpgradeTierTwo;
import com.blocklogic.realfilingreborn.item.custom.RangeUpgradeTierThree;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FilingCabinetBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<FilingCabinetBlock> CODEC = simpleCodec(FilingCabinetBlock::new);

    public FilingCabinetBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new FilingCabinetBlockEntity(blockPos, blockState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity) {
                filingCabinetBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void openFilingCabinetMenu(FilingCabinetBlockEntity blockEntity, ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider(blockEntity, Component.translatable("menu.realfilingreborn.menu_title")), pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity && player.isCrouching()) {
            if (!level.isClientSide()) {
                openFilingCabinetMenu(filingCabinetBlockEntity, (ServerPlayer) player, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity) {
            ItemStack heldItem = player.getItemInHand(hand);

            if (heldItem.getItem() instanceof FilingFolderItem) {
                for (int i = 0; i < 12; i++) {
                    if (filingCabinetBlockEntity.inventory.getStackInSlot(i).isEmpty()) {
                        ItemStack folderStack = heldItem.copy();
                        folderStack.setCount(1);
                        filingCabinetBlockEntity.inventory.setStackInSlot(i, folderStack);
                        heldItem.shrink(1);
                        level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                        return ItemInteractionResult.SUCCESS;
                    }
                }

                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folders_full"), true);
                }
                return ItemInteractionResult.SUCCESS;
            } else if (heldItem.getItem() instanceof NBTFilingFolderItem) {
                for (int i = 0; i < 12; i++) {
                    if (filingCabinetBlockEntity.inventory.getStackInSlot(i).isEmpty()) {
                        ItemStack folderStack = heldItem.copy();
                        folderStack.setCount(1);
                        filingCabinetBlockEntity.inventory.setStackInSlot(i, folderStack);
                        heldItem.shrink(1);
                        level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                        return ItemInteractionResult.SUCCESS;
                    }
                }

                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folders_full"), true);
                }
                return ItemInteractionResult.SUCCESS;
            } else if (heldItem.getItem() instanceof IndexCardItem) {
                if (heldItem.get(ModDataComponents.COORDINATES) == null) {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_card_not_linked")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return ItemInteractionResult.FAIL;
                }

                if (filingCabinetBlockEntity.inventory.getStackInSlot(12).isEmpty()) {
                    ItemStack cardStack = heldItem.copy();
                    cardStack.setCount(1);
                    filingCabinetBlockEntity.inventory.setStackInSlot(12, cardStack);
                    heldItem.shrink(1);
                    level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                } else {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(Component.translatable("message.realfilingreborn.index_occupied"), true);
                    }
                }
                return ItemInteractionResult.SUCCESS;
            } else if (heldItem.getItem() instanceof RangeUpgradeTierOne ||
                    heldItem.getItem() instanceof RangeUpgradeTierTwo ||
                    heldItem.getItem() instanceof RangeUpgradeTierThree) {
                // Handle range upgrade insertion
                if (filingCabinetBlockEntity.inventory.getStackInSlot(13).isEmpty()) {
                    ItemStack upgradeStack = heldItem.copy();
                    upgradeStack.setCount(1);
                    filingCabinetBlockEntity.inventory.setStackInSlot(13, upgradeStack);
                    heldItem.shrink(1);
                    level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                } else {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(Component.translatable("message.realfilingreborn.upgrade_occupied"), true);
                    }
                }
                return ItemInteractionResult.SUCCESS;
            } else if (heldItem.isEmpty()) {
                // Only extract folders, not upgrades or index cards
                for (int i = 11; i >= 0; i--) {
                    ItemStack slotStack = filingCabinetBlockEntity.inventory.getStackInSlot(i);
                    if (!slotStack.isEmpty()) {
                        player.setItemInHand(hand, slotStack.copy());
                        filingCabinetBlockEntity.inventory.setStackInSlot(i, ItemStack.EMPTY);
                        level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
                        return ItemInteractionResult.SUCCESS;
                    }
                }
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.no_folders"), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.FAIL;
    }
}
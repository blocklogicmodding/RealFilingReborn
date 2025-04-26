package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
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

            if (heldItem.getItem() instanceof FilingFolderItem || heldItem.getItem() instanceof NBTFilingFolderItem) {
                // Client early return to prevent visual desync
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                for (int i = 0; i < 12; i++) {
                    if (filingCabinetBlockEntity.inventory.getStackInSlot(i).isEmpty()) {
                        ItemStack folderStack = heldItem.copyWithCount(1);
                        filingCabinetBlockEntity.inventory.setStackInSlot(i, folderStack);
                        heldItem.shrink(1);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                        // Force synchronization
                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        filingCabinetBlockEntity.setChanged();
                        return ItemInteractionResult.SUCCESS;
                    }
                }

                player.displayClientMessage(Component.translatable("message.realfilingreborn.folders_full"), true);
                return ItemInteractionResult.SUCCESS;
            }
            else if (heldItem.getItem() instanceof IndexCardItem) {
                // Client early return to prevent visual desync
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                // Server-side validation
                // 1. Check if card has coordinates FIRST
                if (heldItem.get(ModDataComponents.COORDINATES) == null) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.index_card_not_linked")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return ItemInteractionResult.FAIL;
                }

                // 2. Get coordinates AFTER null check
                BlockPos indexPos = heldItem.get(ModDataComponents.COORDINATES);

                // 3. Verify index existence
                if (!(level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity indexBE)) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.index_no_longer_exists")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return ItemInteractionResult.FAIL;
                }

                // 4. Check capacity BEFORE slot check
                if (!indexBE.canAcceptMoreCabinets()) {
                    // Check if it's at base capacity (64) or max capacity (128)
                    boolean hasUpgrade = !indexBE.inventory.getStackInSlot(0).isEmpty();
                    if (hasUpgrade) {
                        player.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_at_max_capacity")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_at_base_capacity")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return ItemInteractionResult.FAIL;
                }

                // 5. Check cabinet slot availability LAST
                if (!filingCabinetBlockEntity.inventory.getStackInSlot(12).isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.index_occupied")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return ItemInteractionResult.SUCCESS;
                }

                // All checks passed - perform insertion
                ItemStack cardStack = heldItem.copyWithCount(1);
                filingCabinetBlockEntity.inventory.setStackInSlot(12, cardStack);
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                // Force synchronization
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                filingCabinetBlockEntity.setChanged();
                indexBE.invalidateCache();

                return ItemInteractionResult.SUCCESS;
            }

        }
        return ItemInteractionResult.FAIL;
    }
}
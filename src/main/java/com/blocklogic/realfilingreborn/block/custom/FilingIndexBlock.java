package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty ACTIVATION_LEVEL = IntegerProperty.create("activation_level", 0, 2);
    public static final IntegerProperty RANGE_LEVEL = IntegerProperty.create("range_level", 0, 3);
    public static final MapCodec<FilingIndexBlock> CODEC = simpleCodec(FilingIndexBlock::new);
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public FilingIndexBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVATION_LEVEL, 0));
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATION_LEVEL, RANGE_LEVEL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilingIndexBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {
                List<BlockPos> connectedCabinetPositions = new ArrayList<>();

                int searchRange = filingIndexBlockEntity.getRangeFromUpgrade();

                BlockPos.betweenClosed(
                        pos.offset(-searchRange, -searchRange, -searchRange),
                        pos.offset(searchRange, searchRange, searchRange)
                ).forEach(checkPos -> {
                    if (level.isLoaded(checkPos) && level.getBlockEntity(checkPos) instanceof FilingCabinetBlockEntity cabinetBE) {
                        ItemStack indexCardStack = cabinetBE.inventory.getStackInSlot(12);

                        if (!indexCardStack.isEmpty() &&
                                indexCardStack.getItem() instanceof IndexCardItem &&
                                indexCardStack.get(ModDataComponents.COORDINATES) != null &&
                                indexCardStack.get(ModDataComponents.COORDINATES).equals(pos)) {

                            ItemStack resetCard = new ItemStack(indexCardStack.getItem());
                            resetCard.setCount(1);

                            cabinetBE.inventory.setStackInSlot(12, ItemStack.EMPTY);

                            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, resetCard);

                            cabinetBE.setChanged();
                        }
                    }
                });

                filingIndexBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void openFilingIndexMenu(FilingIndexBlockEntity blockEntity, ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider(blockEntity, Component.translatable("menu.realfilingreborn.index_title")), pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity && player.isCrouching()) {
            if (!level.isClientSide()) {
                openFilingIndexMenu(filingIndexBlockEntity, (ServerPlayer) player, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {

            if (stack.getItem() instanceof CapacityUpgradeItem) {

                if (filingIndexBlockEntity.inventory.getStackInSlot(0).isEmpty()) {
                    ItemStack upgradeStack = stack.copy();
                    upgradeStack.setCount(1);
                    filingIndexBlockEntity.inventory.setStackInSlot(0, upgradeStack);
                    stack.shrink(1);
                    level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                    filingIndexBlockEntity.invalidateCache();
                    return ItemInteractionResult.SUCCESS;
                } else {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(Component.translatable("message.realfilingreborn.upgrade_occupied"), true);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        return ItemInteractionResult.FAIL;
    }
}
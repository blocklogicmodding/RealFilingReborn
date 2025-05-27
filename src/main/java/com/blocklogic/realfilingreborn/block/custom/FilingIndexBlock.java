package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.item.Item;
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

public class FilingIndexBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<FilingIndexBlock> CODEC = simpleCodec(FilingIndexBlock::new);

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
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CONNECTED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new FilingIndexBlockEntity(blockPos, blockState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {
                // Clear all connected cabinets BEFORE dropping items
                if (!level.isClientSide()) {
                    try {
                        for (Long cabinetLong : new ArrayList<>(filingIndexBlockEntity.getConnectedCabinets().getConnectedCabinets())) {
                            BlockPos cabinetPos = BlockPos.of(cabinetLong);
                            BlockEntity entity = level.getBlockEntity(cabinetPos);
                            if (entity instanceof FilingCabinetBlockEntity filingCabinet) {
                                filingCabinet.clearControllerPos();
                            } else if (entity instanceof FluidCabinetBlockEntity fluidCabinet) {
                                fluidCabinet.clearControllerPos();
                            }
                        }
                    } catch (Exception e) {
                        // Handle cleanup errors silently
                    }
                }

                filingIndexBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Updates the connected state of the Filing Index block
     */
    public void updateConnectedState(Level level, BlockPos pos, boolean hasConnections) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.getValue(CONNECTED) != hasConnections) {
            BlockState newState = currentState.setValue(CONNECTED, hasConnections);
            level.setBlock(pos, newState, Block.UPDATE_ALL);
        }
    }

    private void openFilingIndexMenu(FilingIndexBlockEntity blockEntity, ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, playerEntity) -> new FilingIndexMenu(id, inventory, blockEntity),
                Component.translatable("menu.realfilingreborn.index_menu_title")
        ), pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {
            Item item = stack.getItem();

            // Handle upgrade items
            boolean isValidUpgrade = item instanceof IndexRangerUpgradeGold
                    || item instanceof IndexRangerUpgradeDiamond
                    || item instanceof IndexRangerUpgradeNetherite;

            if (isValidUpgrade) {
                if (filingIndexBlockEntity.inventory.getStackInSlot(0).isEmpty() && !stack.isEmpty()) {
                    filingIndexBlockEntity.inventory.insertItem(0, stack.copy(), false);
                    stack.shrink(1);
                    level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                    return ItemInteractionResult.SUCCESS;
                } else {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(Component.translatable("message.realfilingreborn.index_full"), true);
                    }

                    return ItemInteractionResult.SUCCESS;
                }
            }

            // Handle ledger item - let it handle the interaction
            if (item instanceof LedgerItem) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            // Default: open GUI
            if (!level.isClientSide()) {
                openFilingIndexMenu(filingIndexBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
            }

            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.FAIL;
    }
}
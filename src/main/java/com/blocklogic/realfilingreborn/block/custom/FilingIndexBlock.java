package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.component.LedgerData;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FilingIndexBlock extends BaseEntityBlock {
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<FilingCabinetBlock> CODEC = simpleCodec(FilingCabinetBlock::new);

    // ADDED: Connected state property
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public FilingIndexBlock(Properties properties) {
        super(properties);
        // ADDED: Default to not connected
        this.registerDefaultState(this.stateDefinition.any().setValue(CONNECTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // ADDED: Register the connected property
        builder.add(CONNECTED);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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

    // ADDED: Method to update connected state
    public static void updateConnectedState(Level level, BlockPos pos) {
        if (level.isClientSide()) return;

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof FilingIndexBlock)) return;

        if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity indexEntity) {
            boolean hasConnections = indexEntity.getLinkedCabinetCount() > 0;
            boolean currentlyConnected = currentState.getValue(CONNECTED);

            if (hasConnections != currentlyConnected) {
                BlockState newState = currentState.setValue(CONNECTED, hasConnections);
                level.setBlock(pos, newState, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {
                // Clear all linked cabinets first
                filingIndexBlockEntity.clearAllLinkedCabinets();

                // Clear controller from nearby ledgers
                clearControllerFromNearbyLedgers(level, pos);

                filingIndexBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void clearControllerFromNearbyLedgers(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) return;

        level.players().forEach(player -> {
            if (player.distanceToSqr(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ()) <= 64 * 64) {
                clearControllerFromPlayerLedgers(player, controllerPos);
            }
        });
    }

    private void clearControllerFromPlayerLedgers(Player player, BlockPos controllerPos) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof LedgerItem) {
            clearControllerFromLedger(mainHand, controllerPos, player);
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof LedgerItem) {
            clearControllerFromLedger(offHand, controllerPos, player);
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LedgerItem) {
                clearControllerFromLedger(stack, controllerPos, player);
            }
        }
    }

    private void clearControllerFromLedger(ItemStack ledgerStack, BlockPos controllerPos, Player player) {
        LedgerData data = ledgerStack.getOrDefault(
                ModDataComponents.LEDGER_DATA.get(),
                LedgerData.DEFAULT
        );

        if (data.selectedController() != null && data.selectedController().equals(controllerPos)) {
            LedgerData newData = data.withSelectedController(null);
            ledgerStack.set(ModDataComponents.LEDGER_DATA.get(), newData);

            Component message = Component.translatable("item.realfilingreborn.ledger.controller.cleared");
            player.displayClientMessage(message, true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FilingIndexBlockEntity filingIndexBlockEntity) {
            if (!level.isClientSide()) {
                ((ServerPlayer) player).openMenu(new SimpleMenuProvider(filingIndexBlockEntity, Component.translatable("menu.realfilingreborn.filing_index")), pos);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.SUCCESS;
    }
}
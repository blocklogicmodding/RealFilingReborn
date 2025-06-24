package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.screen.custom.FluidCabinetMenu;
import com.blocklogic.realfilingreborn.util.FluidHelper;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class FluidCabinetBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<FluidCabinetBlock> CODEC = simpleCodec(FluidCabinetBlock::new);

    public FluidCabinetBlock(Properties properties) {
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
        return new FluidCabinetBlockEntity(blockPos, blockState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof FluidCabinetBlockEntity fluidCabinetBlockEntity) {
                BlockPos controllerPos = fluidCabinetBlockEntity.getControllerPos();
                if (controllerPos != null) {
                    if (level.getBlockEntity(controllerPos) instanceof FilingIndexBlockEntity indexEntity) {
                        indexEntity.removeCabinet(pos);
                    }
                }

                fluidCabinetBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void openFluidCabinetMenu(FluidCabinetBlockEntity blockEntity, ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, playerEntity) -> new FluidCabinetMenu(id, inventory, blockEntity),
                Component.translatable("menu.realfilingreborn.fluid_cabinet_menu_title")
        ), pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FluidCabinetBlockEntity fluidCabinetBlockEntity && player.isCrouching()) {
            if (!level.isClientSide()) {
                openFluidCabinetMenu(fluidCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof FluidCabinetBlockEntity fluidCabinetBlockEntity) {
            ItemStack heldItem = player.getItemInHand(hand);

            Direction facing = state.getValue(FACING);
            if (hitResult.getDirection() == facing) {
                if (heldItem.getItem() == Items.BUCKET) {
                    if (level.isClientSide()) {
                        return ItemInteractionResult.SUCCESS;
                    }

                    int targetSlot = getQuadFromHitResult(hitResult, facing);
                    if (targetSlot >= 0 && targetSlot < 4) {
                        return extractFromSlot(fluidCabinetBlockEntity, targetSlot, player, level, pos, state);
                    }
                }
            }

            if (heldItem.getItem() instanceof FluidCanisterItem) {
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                for (int i = 0; i < 4; i++) {
                    if (fluidCabinetBlockEntity.inventory.getStackInSlot(i).isEmpty()) {
                        ItemStack canisterStack = heldItem.copyWithCount(1);
                        fluidCabinetBlockEntity.inventory.setStackInSlot(i, canisterStack);
                        heldItem.shrink(1);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        fluidCabinetBlockEntity.setChanged();
                        return ItemInteractionResult.SUCCESS;
                    }
                }

                player.displayClientMessage(Component.translatable("message.realfilingreborn.canisters_full"), true);
                return ItemInteractionResult.SUCCESS;
            }
            else if (heldItem.getItem() instanceof BucketItem bucketItem && bucketItem.content != Fluids.EMPTY) {
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                Fluid fluid = bucketItem.content;
                ResourceLocation fluidId = fluid.builtInRegistryHolder().key().location();

                for (int i = 0; i < 4; i++) {
                    ItemStack canisterStack = fluidCabinetBlockEntity.inventory.getStackInSlot(i);

                    if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

                        if (contents != null) {
                            if (contents.storedFluidId().isEmpty()) {
                                FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                        Optional.of(fluidId),
                                        1000
                                );
                                canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);

                                heldItem.shrink(1);
                                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                                if (!player.getInventory().add(emptyBucket)) {
                                    player.drop(emptyBucket, false);
                                }

                                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1.5f);
                                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                fluidCabinetBlockEntity.setChanged();
                                return ItemInteractionResult.SUCCESS;
                            } else if (contents.storedFluidId().get().equals(fluidId)) {
                                int maxToAdd = Integer.MAX_VALUE - contents.amount();
                                int toAdd = Math.min(1000, maxToAdd);

                                if (toAdd >= 1000) {
                                    FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                                            contents.storedFluidId(),
                                            contents.amount() + 1000
                                    );
                                    canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);

                                    heldItem.shrink(1);
                                    ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                                    if (!player.getInventory().add(emptyBucket)) {
                                        player.drop(emptyBucket, false);
                                    }

                                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1.5f);
                                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                    fluidCabinetBlockEntity.setChanged();
                                    return ItemInteractionResult.SUCCESS;
                                }
                            }
                        }
                    }
                }

                player.displayClientMessage(Component.translatable("message.realfilingreborn.no_compatible_canister"), true);
                return ItemInteractionResult.SUCCESS;
            }

            if (!level.isClientSide()) {
                openFluidCabinetMenu(fluidCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.FAIL;
    }

    private int getQuadFromHitResult(BlockHitResult hitResult, Direction facing) {
        Vec3 hitPos = hitResult.getLocation();

        double relativeX = hitPos.x - Math.floor(hitPos.x);
        double relativeY = hitPos.y - Math.floor(hitPos.y);
        double relativeZ = hitPos.z - Math.floor(hitPos.z);

        double faceX, faceY;

        switch (facing) {
            case NORTH:
                faceX = 1.0 - relativeX;
                faceY = relativeY;
                break;
            case SOUTH:
                faceX = relativeX;
                faceY = relativeY;
                break;
            case EAST:
                faceX = 1.0 - relativeZ;
                faceY = relativeY;
                break;
            case WEST:
                faceX = relativeZ;
                faceY = relativeY;
                break;
            default:
                return -1;
        }

        boolean isLeft = faceX < 0.5;
        boolean isTop = faceY > 0.5;

        if (isTop && isLeft) return 0;
        if (isTop && !isLeft) return 1;
        if (!isTop && isLeft) return 2;
        if (!isTop && !isLeft) return 3;

        return -1;
    }

    private ItemInteractionResult extractFromSlot(FluidCabinetBlockEntity blockEntity, int slot, Player player,
                                                  Level level, BlockPos pos, BlockState state) {
        ItemStack canisterStack = blockEntity.inventory.getStackInSlot(slot);

        if (canisterStack.isEmpty() || !(canisterStack.getItem() instanceof FluidCanisterItem)) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.no_fluid_in_slot"), true);
            return ItemInteractionResult.SUCCESS;
        }

        FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());
        if (contents == null || contents.storedFluidId().isEmpty() || contents.amount() < 1000) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.not_enough_fluid_in_slot"), true);
            return ItemInteractionResult.SUCCESS;
        }

        ResourceLocation fluidId = contents.storedFluidId().get();
        ItemStack bucketToGive = FluidHelper.getBucketForFluid(fluidId);

        if (bucketToGive.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.no_bucket_for_fluid"), true);
            return ItemInteractionResult.SUCCESS;
        }

        FluidCanisterItem.CanisterContents newContents = new FluidCanisterItem.CanisterContents(
                contents.storedFluidId(),
                contents.amount() - 1000
        );
        canisterStack.set(FluidCanisterItem.CANISTER_CONTENTS.value(), newContents);

        player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);

        if (!player.getInventory().add(bucketToGive)) {
            player.drop(bucketToGive, false);
        }

        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1.5f);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        blockEntity.setChanged();

        player.displayClientMessage(Component.translatable("message.realfilingreborn.fluid_extracted"), true);
        return ItemInteractionResult.SUCCESS;
    }

    private ItemStack getBucketForFluid(ResourceLocation fluidId) {
        if (fluidId.equals(Fluids.WATER.builtInRegistryHolder().key().location())) {
            return new ItemStack(Items.WATER_BUCKET);
        } else if (fluidId.equals(Fluids.LAVA.builtInRegistryHolder().key().location())) {
            return new ItemStack(Items.LAVA_BUCKET);
        }

        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item instanceof BucketItem bucketItem && bucketItem.content == fluid) {
                        return new ItemStack(item);
                    }
                }
            }
        } catch (Exception e) {
        }

        return ItemStack.EMPTY;
    }
}
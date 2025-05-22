package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetMenu;
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
import net.minecraft.world.MenuProvider;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, playerEntity) -> new FilingCabinetMenu(id, inventory, blockEntity),
                Component.translatable("menu.realfilingreborn.menu_title")
        ), pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity && player.isCrouching()) {
            if (!level.isClientSide()) {
                openFilingCabinetMenu(filingCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS,  1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity) {
            ItemStack heldItem = player.getItemInHand(hand);

            if (heldItem.getItem() instanceof FilingFolderItem || heldItem.getItem() instanceof NBTFilingFolderItem) {
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                for (int i = 0; i < 5; i++) {
                    if (filingCabinetBlockEntity.inventory.getStackInSlot(i).isEmpty()) {
                        ItemStack folderStack = heldItem.copyWithCount(1);
                        filingCabinetBlockEntity.inventory.setStackInSlot(i, folderStack);
                        heldItem.shrink(1);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);

                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        filingCabinetBlockEntity.setChanged();
                        return ItemInteractionResult.SUCCESS;
                    }
                }

                player.displayClientMessage(Component.translatable("message.realfilingreborn.folders_full"), true);
                return ItemInteractionResult.SUCCESS;
            }
            else if (!heldItem.isEmpty() && !(heldItem.getItem() instanceof FilingFolderItem) && !(heldItem.getItem() instanceof NBTFilingFolderItem)) {
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                boolean hasNbt = NBTFilingFolderItem.hasSignificantNBT(heldItem);
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());

                for (int i = 0; i < 5; i++) {
                    ItemStack folderStack = filingCabinetBlockEntity.inventory.getStackInSlot(i);

                    if (!folderStack.isEmpty()) {
                        if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                            if (hasNbt) {
                                continue;
                            }

                            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

                            if (contents != null) {
                                if (contents.storedItemId().isEmpty()) {
                                    FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                            Optional.of(itemId),
                                            heldItem.getCount()
                                    );
                                    folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                                    heldItem.shrink(heldItem.getCount());

                                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
                                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                    filingCabinetBlockEntity.setChanged();
                                    return ItemInteractionResult.SUCCESS;
                                } else if (contents.storedItemId().get().equals(itemId)) {
                                    int maxToAdd = Integer.MAX_VALUE - contents.count();
                                    int toAdd = Math.min(heldItem.getCount(), maxToAdd);

                                    if (toAdd > 0) {
                                        FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                                contents.storedItemId(),
                                                contents.count() + toAdd
                                        );
                                        folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                                        heldItem.shrink(toAdd);

                                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
                                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                        filingCabinetBlockEntity.setChanged();
                                        return ItemInteractionResult.SUCCESS;
                                    }
                                }
                            }
                        }
                        else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                            if (!hasNbt) {
                                continue;
                            }

                            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

                            if (contents != null) {
                                if (contents.storedItemId().isEmpty()) {
                                    List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>();

                                    int toAdd = Math.min(heldItem.getCount(), NBTFilingFolderItem.MAX_NBT_ITEMS);
                                    for (int count = 0; count < toAdd; count++) {
                                        ItemStack singleItem = heldItem.copy();
                                        singleItem.setCount(1);
                                        newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                                    }

                                    NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                            Optional.of(itemId),
                                            newItems
                                    );
                                    folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                                    heldItem.shrink(toAdd);

                                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
                                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                    filingCabinetBlockEntity.setChanged();
                                    return ItemInteractionResult.SUCCESS;
                                } else if (contents.storedItemId().get().equals(itemId)) {
                                    if (contents.storedItems().size() < NBTFilingFolderItem.MAX_NBT_ITEMS) {
                                        List<NBTFilingFolderItem.SerializedItemStack> newItems = new ArrayList<>(contents.storedItems());

                                        int availableSpace = NBTFilingFolderItem.MAX_NBT_ITEMS - newItems.size();
                                        int toAdd = Math.min(heldItem.getCount(), availableSpace);

                                        for (int count = 0; count < toAdd; count++) {
                                            ItemStack singleItem = heldItem.copy();
                                            singleItem.setCount(1);
                                            newItems.add(new NBTFilingFolderItem.SerializedItemStack(singleItem));
                                        }

                                        NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                                                contents.storedItemId(),
                                                newItems
                                        );
                                        folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);
                                        heldItem.shrink(toAdd);

                                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
                                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                        filingCabinetBlockEntity.setChanged();
                                        return ItemInteractionResult.SUCCESS;
                                    }
                                }
                            }
                        }
                    }
                }

                player.displayClientMessage(Component.translatable("message.realfilingreborn.no_compatible_folder"), true);
                return ItemInteractionResult.SUCCESS;
            }

            if (!level.isClientSide()) {
                openFilingCabinetMenu(filingCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS,  1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.FAIL;
    }
}
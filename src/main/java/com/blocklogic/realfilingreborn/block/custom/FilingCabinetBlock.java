package com.blocklogic.realfilingreborn.block.custom;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.phys.Vec3;
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
                // Notify controller if linked
                BlockPos controllerPos = filingCabinetBlockEntity.getControllerPos();
                if (controllerPos != null) {
                    if (level.getBlockEntity(controllerPos) instanceof FilingIndexBlockEntity indexEntity) {
                        indexEntity.removeCabinet(pos);
                    }
                }

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
        if (!(level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetBlockEntity)) {
            return ItemInteractionResult.FAIL;
        }

        if (player.isCrouching()) {
            if (!level.isClientSide()) {
                openFilingCabinetMenu(filingCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.isEmpty()) {
            Direction facing = state.getValue(FACING);
            if (hitResult.getDirection() == facing) {
                if (level.isClientSide()) {
                    return ItemInteractionResult.SUCCESS;
                }

                int targetSlot = getSlotFromHitResult(hitResult, facing);
                if (targetSlot >= 0 && targetSlot < 5) {
                    InteractionResult result = extractFromSlot(filingCabinetBlockEntity, targetSlot, player, level, pos, state);
                    return result == InteractionResult.SUCCESS ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
                }
            }

            return ItemInteractionResult.SUCCESS;
        }

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

        ItemInteractionResult storageResult = handleItemStorage(heldItem, filingCabinetBlockEntity, player, level, pos, state);

        if (storageResult == ItemInteractionResult.FAIL) {
            if (!level.isClientSide()) {
                openFilingCabinetMenu(filingCabinetBlockEntity, (ServerPlayer) player, pos);
                level.playSound(player, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1F, 1F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return storageResult;
    }

    private ItemInteractionResult handleItemStorage(ItemStack heldItem, FilingCabinetBlockEntity filingCabinetBlockEntity, Player player, Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        boolean hasNbt = NBTFilingFolderItem.hasSignificantNBT(heldItem);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());

        for (int i = 0; i < 5; i++) {
            ItemStack folderStack = filingCabinetBlockEntity.inventory.getStackInSlot(i);

            if (!folderStack.isEmpty()) {
                if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                    if (hasNbt) continue;

                    FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                    if (contents != null) {
                        if (contents.storedItemId().isEmpty()) {
                            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                                    Optional.of(itemId), heldItem.getCount());
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
                                        contents.storedItemId(), contents.count() + toAdd);
                                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);
                                heldItem.shrink(toAdd);

                                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.5f);
                                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                                filingCabinetBlockEntity.setChanged();
                                return ItemInteractionResult.SUCCESS;
                            }
                        }
                    }
                } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                    if (!hasNbt) continue;

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
                                    Optional.of(itemId), newItems);
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
                                        contents.storedItemId(), newItems);
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
        return ItemInteractionResult.FAIL;
    }

    private int getSlotFromHitResult(BlockHitResult hitResult, Direction facing) {
        Vec3 hitPos = hitResult.getLocation();
        double relativeX = hitPos.x - Math.floor(hitPos.x);
        double relativeZ = hitPos.z - Math.floor(hitPos.z);

        double faceX;
        switch (facing) {
            case NORTH: faceX = 1.0 - relativeX; break;
            case SOUTH: faceX = relativeX; break;
            case EAST: faceX = 1.0 - relativeZ; break;
            case WEST: faceX = relativeZ; break;
            default: return -1;
        }

        if (faceX < 0.2) return 0;
        else if (faceX < 0.4) return 1;
        else if (faceX < 0.6) return 2;
        else if (faceX < 0.8) return 3;
        else return 4;
    }

    private InteractionResult extractFromSlot(FilingCabinetBlockEntity blockEntity, int slot, Player player, Level level, BlockPos pos, BlockState state) {
        ItemStack folderStack = blockEntity.inventory.getStackInSlot(slot);

        if (folderStack.isEmpty() || (!(folderStack.getItem() instanceof FilingFolderItem) && !(folderStack.getItem() instanceof NBTFilingFolderItem))) {
            return InteractionResult.SUCCESS;
        }

        if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

            if (contents == null || contents.storedItemId().isEmpty() || contents.count() <= 0) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
                return InteractionResult.SUCCESS;
            }

            ResourceLocation itemId = contents.storedItemId().get();
            Item item = BuiltInRegistries.ITEM.get(itemId);

            ItemStack dummyStack = new ItemStack(item);
            int maxStackSize = item.getMaxStackSize(dummyStack);
            int extractAmount = Math.min(Math.min(contents.count(), maxStackSize), 64);

            if (extractAmount <= 0) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
                return InteractionResult.SUCCESS;
            }

            ItemStack extractedStack = new ItemStack(item, extractAmount);
            int newCount = contents.count() - extractAmount;
            FilingFolderItem.FolderContents newContents = new FilingFolderItem.FolderContents(
                    contents.storedItemId(), Math.max(0, newCount));
            folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(), newContents);

            if (!player.getInventory().add(extractedStack)) {
                player.drop(extractedStack, false);
            }

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.0f);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            blockEntity.setChanged();

            return InteractionResult.SUCCESS;
        }
        else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

            if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
                return InteractionResult.SUCCESS;
            }

            List<NBTFilingFolderItem.SerializedItemStack> items = new ArrayList<>(contents.storedItems());
            NBTFilingFolderItem.SerializedItemStack serializedItem = items.remove(items.size() - 1);
            ItemStack extracted = serializedItem.stack().copy();

            NBTFilingFolderItem.NBTFolderContents newContents = new NBTFilingFolderItem.NBTFolderContents(
                    contents.storedItemId(), items);
            folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(), newContents);

            if (!player.getInventory().add(extracted)) {
                player.drop(extracted, false);
            }

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1.0f);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            blockEntity.setChanged();

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }
}
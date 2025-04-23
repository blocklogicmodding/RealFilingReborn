package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlockEntity extends BlockEntity {

    private List<IItemHandler> cachedHandlers = new ArrayList<>();
    private long lastCacheUpdate = -1;
    private static final long CACHE_INTERVAL = 100;
    private int previousCabinetCount = 0;

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    public List<IItemHandler> getCabinetItemHandlers() {
        if (level == null || level.isClientSide()) return List.of();

        long gameTime = level.getGameTime();
        if (gameTime - lastCacheUpdate >= CACHE_INTERVAL) {
            lastCacheUpdate = gameTime;
            cachedHandlers.clear();

            int radius = 16;
            BlockPos origin = getBlockPos();

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos cabinetPos = origin.offset(x, y, z);
                        if (level.isLoaded(cabinetPos)) {
                            BlockEntity be = level.getBlockEntity(cabinetPos);
                            if (be instanceof FilingCabinetBlockEntity cabinet) {
                                ItemStack indexCardStack = cabinet.inventory.getStackInSlot(12);
                                if (!indexCardStack.isEmpty()
                                        && indexCardStack.getItem() instanceof IndexCardItem
                                        && indexCardStack.get(ModDataComponents.COORDINATES) != null
                                        && indexCardStack.get(ModDataComponents.COORDINATES).equals(getBlockPos())) {

                                    IItemHandler handler = cabinet.getCapabilityHandler(null);
                                    if (handler != null) {
                                        cachedHandlers.add(handler);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            updateActivationLevel();
        }

        return cachedHandlers;
    }

    private void updateActivationLevel() {
        int cabinetCount = cachedHandlers.size();

        // Only update if the count changed
        if (cabinetCount != previousCabinetCount) {
            previousCabinetCount = cabinetCount;

            int activationLevel;
            if (cabinetCount == 0) {
                activationLevel = 0;
            } else if (cabinetCount >= 1 && cabinetCount <= 8) {
                activationLevel = 1;
            } else {
                activationLevel = 2;
            }

            if (level != null && !level.isClientSide()) {
                BlockState currentState = level.getBlockState(getBlockPos());
                if (currentState.getValue(FilingIndexBlock.ACTIVATION_LEVEL) != activationLevel) {
                    level.setBlock(getBlockPos(),
                            currentState.setValue(FilingIndexBlock.ACTIVATION_LEVEL, activationLevel),
                            Block.UPDATE_ALL);
                }
            }
        }
    }

    public int getCabinetCount() {
        return getCabinetItemHandlers().size();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("PreviousCabinetCount", previousCabinetCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        previousCabinetCount = tag.getInt("PreviousCabinetCount");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            lastCacheUpdate = -1;
            getCabinetItemHandlers();
        }
    }
}
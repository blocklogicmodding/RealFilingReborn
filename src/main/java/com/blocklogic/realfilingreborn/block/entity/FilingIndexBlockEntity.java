package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.IndexCardItem;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IItemHandler> cachedHandlers = new ArrayList<>();
    private long lastCacheUpdate = -1;
    private static final long CACHE_INTERVAL = 100;
    private int previousCabinetCount = 0;
    private boolean cacheDirty = true;
    private static List<BlockPos> SPHERE_POSITIONS_CACHE = null;
    private static final int SPHERE_RADIUS = 16;


    // Static sphere cache for radius 16
    private static List<BlockPos> getSpherePositionsCache() {
        if (SPHERE_POSITIONS_CACHE == null) {
            SPHERE_POSITIONS_CACHE = List.copyOf(getSpherePositions(BlockPos.ZERO, SPHERE_RADIUS));
        }
        return SPHERE_POSITIONS_CACHE;
    }

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    public void invalidateCache() {
        cacheDirty = true;
        lastCacheUpdate = -CACHE_INTERVAL;
    }

    public List<IItemHandler> getCabinetItemHandlers() {
        if (level == null || level.isClientSide()) return List.copyOf(cachedHandlers);

        if(!level.isAreaLoaded(getBlockPos(), SPHERE_RADIUS)) {
            return List.copyOf(cachedHandlers);
        }

        long gameTime = level.getGameTime();

        // Early return if cache is still valid
        if (!cacheDirty && gameTime - lastCacheUpdate < CACHE_INTERVAL) {
            return List.copyOf(cachedHandlers);
        }

        // Start performance monitoring
        long startTime = System.nanoTime();

        // Rebuild cache
        cachedHandlers.clear();
        List<BlockPos> searchArea = getSpherePositionsCache().stream() // CORRECT
                .map(pos -> pos.offset(getBlockPos()))
                .toList();

        for (BlockPos cabinetPos : searchArea) {
            if (level.isLoaded(cabinetPos)) {
                BlockEntity be = level.getBlockEntity(cabinetPos);
                if (be instanceof FilingCabinetBlockEntity cabinet) {
                    ItemStack indexCardStack = cabinet.inventory.getStackInSlot(12);
                    if (isValidIndexCard(indexCardStack)) {
                        IItemHandler handler = cabinet.getCapabilityHandler(null);
                        if (handler != null) {
                            cachedHandlers.add(handler);
                        }
                    }
                }
            }
        }

        cacheDirty = false;
        lastCacheUpdate = gameTime;
        updateActivationLevel();

        // Performance logging
        if (LOGGER.isDebugEnabled()) {
            double ms = (System.nanoTime() - startTime) / 1_000_000.0;
            LOGGER.debug("Cache rebuilt in {}ms ({} cabinets)", ms, cachedHandlers.size());
        }

        return List.copyOf(cachedHandlers);
    }

    private boolean isValidIndexCard(ItemStack stack) {
        return !stack.isEmpty() &&
                stack.getItem() instanceof IndexCardItem &&
                stack.get(ModDataComponents.COORDINATES) != null &&
                stack.get(ModDataComponents.COORDINATES).equals(getBlockPos());
    }

    private static List<BlockPos> getSpherePositions(BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        int radiusSq = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radiusSq) {
                        positions.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    private void updateActivationLevel() {
        int cabinetCount = cachedHandlers.size();

        if (cabinetCount != previousCabinetCount) {
            previousCabinetCount = cabinetCount;

            int activationLevel = cabinetCount == 0 ? 0 :
                    (cabinetCount <= 8 ? 1 : 2);

            if (level != null && !level.isClientSide()) {
                BlockState currentState = level.getBlockState(getBlockPos());
                if (currentState.getValue(FilingIndexBlock.ACTIVATION_LEVEL) != activationLevel) {
                    level.setBlock(getBlockPos(),
                            currentState.setValue(FilingIndexBlock.ACTIVATION_LEVEL, activationLevel),
                            Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
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
            invalidateCache();
            getCabinetItemHandlers();
        }
    }
}

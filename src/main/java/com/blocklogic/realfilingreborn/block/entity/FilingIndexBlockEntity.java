package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IItemHandler> cachedHandlers = new ArrayList<>();
    private long lastCacheUpdate = -1;
    private static final long CACHE_INTERVAL = 100;
    private int previousCabinetCount = 0;
    private boolean cacheDirty = true;
    private static List<BlockPos>[] SPHERE_POSITIONS_CACHE = new List[4];
    private static final int[] RANGE_TIERS = {8, 16, 24, 32};
    private int cachedRange;

    private List<BlockPos> getSpherePositionsCache() {
        int rangeLevel = 0;
        ItemStack stack = inventory.getStackInSlot(0);

        if (stack.getItem() instanceof RangeUpgradeTierThree) {
            rangeLevel = 3;
        } else if (stack.getItem() instanceof RangeUpgradeTierTwo) {
            rangeLevel = 2;
        } else if (stack.getItem() instanceof RangeUpgradeTierOne) {
            rangeLevel = 1;
        }

        if (SPHERE_POSITIONS_CACHE[rangeLevel] == null) {
            SPHERE_POSITIONS_CACHE[rangeLevel] = List.copyOf(getSpherePositions(BlockPos.ZERO, RANGE_TIERS[rangeLevel]));
        }
        return SPHERE_POSITIONS_CACHE[rangeLevel];
    }

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
        this.cachedRange = 8;
    }

    public void invalidateCache() {
        cacheDirty = true;
        lastCacheUpdate = -CACHE_INTERVAL;
    }

    public List<IItemHandler> getCabinetItemHandlers() {
        if (level == null || level.isClientSide()) return List.copyOf(cachedHandlers);

        int currentRange = getRangeFromUpgrade();
        if(!level.isAreaLoaded(getBlockPos(), currentRange)) {
            return List.copyOf(cachedHandlers);
        }

        long gameTime = level.getGameTime();

        if (!cacheDirty && gameTime - lastCacheUpdate < CACHE_INTERVAL) {
            return List.copyOf(cachedHandlers);
        }

        long startTime = System.nanoTime();

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

    public int getRangeFromUpgrade() {
        return cachedRange;
    }

    public void updateRangeLevelVisual() {
        if (level == null || level.isClientSide()) return;

        int rangeLevel = 0;
        ItemStack stack = inventory.getStackInSlot(0);

        if (stack.getItem() instanceof RangeUpgradeTierThree) {
            rangeLevel = 3;
        } else if (stack.getItem() instanceof RangeUpgradeTierTwo) {
            rangeLevel = 2;
        } else if (stack.getItem() instanceof RangeUpgradeTierOne) {
            rangeLevel = 1;
        }

        BlockState currentState = level.getBlockState(getBlockPos());
        if (currentState.getValue(FilingIndexBlock.RANGE_LEVEL) != rangeLevel) {
            level.setBlock(getBlockPos(),
                    currentState.setValue(FilingIndexBlock.RANGE_LEVEL, rangeLevel),
                    Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
    }

    public int getCabinetCount() {
        return getCabinetItemHandlers().size();
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("PreviousCabinetCount", previousCabinetCount);
        tag.putInt("CachedRange", cachedRange);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        previousCabinetCount = tag.getInt("PreviousCabinetCount");
        cachedRange = tag.getInt("CachedRange");
    }

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof RangeUpgradeTierOne ||
                    stack.getItem() instanceof RangeUpgradeTierTwo ||
                    stack.getItem() instanceof RangeUpgradeTierThree;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) {
                updateRangeLevelVisual();
                updateCachedRange();
                invalidateCache();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    };

    private void updateCachedRange() {
        cachedRange = calculateRange();
    }

    private int calculateRange() {
        ItemStack upgradeStack = inventory.getStackInSlot(0);
        if (upgradeStack.isEmpty()) {
            return 8;
        } else if (upgradeStack.getItem() instanceof RangeUpgradeTierOne) {
            return 16;
        } else if (upgradeStack.getItem() instanceof RangeUpgradeTierTwo) {
            return 24;
        } else if (upgradeStack.getItem() instanceof RangeUpgradeTierThree) {
            return 32;
        }
        return 8;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("blockentity.realfilingreborn.inde_name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new FilingIndexMenu(i, inventory, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            invalidateCache();
            getCabinetItemHandlers();
            updateRangeLevelVisual();
            updateCachedRange();
        }
    }
}
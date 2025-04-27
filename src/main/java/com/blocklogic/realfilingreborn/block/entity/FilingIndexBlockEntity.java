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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IItemHandler> connectedHandlers = new ArrayList<>();
    private final Set<BlockPos> connectedCabinetPositions = new HashSet<>();
    private boolean scanNeeded = true;
    private int previousCabinetCount = 0;
    private final List<BlockPos>[] boxPositionsCache = new List[2];
    private static final int[] RANGE_TIERS = {8, 16};
    private int cachedRange;
    private static final int BASE_CABINET_LIMIT = 64;
    private static final int UPGRADED_CABINET_LIMIT = 128;

    public boolean canAcceptMoreCabinets() {
        int currentLimit = inventory.getStackInSlot(0).isEmpty() ? BASE_CABINET_LIMIT : UPGRADED_CABINET_LIMIT;
        return getCabinetCount() < currentLimit;
    }

    private List<BlockPos> getBoxPositionsCache() {
        int rangeLevel = 0;
        ItemStack stack = inventory.getStackInSlot(0);

        if (stack.getItem() instanceof CapacityUpgradeItem) {
            rangeLevel = 1;
        }

        if (boxPositionsCache[rangeLevel] == null) {
            boxPositionsCache[rangeLevel] = List.copyOf(getBoxPositions(BlockPos.ZERO, RANGE_TIERS[rangeLevel]));
        }
        return boxPositionsCache[rangeLevel];
    }

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
        this.cachedRange = 8;
        this.boxPositionsCache[0] = null;
        this.boxPositionsCache[1] = null;
    }

    /**
     * Called when a cabinet is connected or disconnected from this index
     * This marks that a scan is needed and performs it immediately
     */
    public void notifyCabinetChanged() {
        scanNeeded = true;
        if (level != null && !level.isClientSide()) {
            performFullScan();
        }
    }

    /**
     * Force the cache to be rebuilt on the next access
     */
    public void invalidateCache() {
        scanNeeded = true;
        boxPositionsCache[0] = null;
        boxPositionsCache[1] = null;
    }

    /**
     * Get all cabinet item handlers connected to this index
     * Only performs a scan if one is needed due to changes
     */
    public List<IItemHandler> getCabinetItemHandlers() {
        if (level == null || level.isClientSide()) {
            return List.copyOf(connectedHandlers);
        }

        if (scanNeeded) {
            performFullScan();
        }

        return List.copyOf(connectedHandlers);
    }

    /**
     * Perform a full scan of the area to find all connected cabinets
     * Only called when necessary (after a change)
     */
    private void performFullScan() {
        if (level == null || level.isClientSide()) return;

        long startTime = System.nanoTime();
        connectedHandlers.clear();
        connectedCabinetPositions.clear();

        int currentRange = getRangeFromUpgrade();
        if(!level.isAreaLoaded(getBlockPos(), currentRange)) {
            scanNeeded = false;
            return;
        }

        List<BlockPos> searchArea = getBoxPositionsCache().stream()
                .map(pos -> pos.offset(getBlockPos()))
                .toList();

        int currentLimit = inventory.getStackInSlot(0).isEmpty() ? BASE_CABINET_LIMIT : UPGRADED_CABINET_LIMIT;
        int cabinetCount = 0;

        for (BlockPos cabinetPos : searchArea) {
            if (cabinetCount >= currentLimit) {
                break;
            }

            if (level.isLoaded(cabinetPos)) {
                BlockEntity be = level.getBlockEntity(cabinetPos);
                if (be instanceof FilingCabinetBlockEntity cabinet) {
                    ItemStack indexCardStack = cabinet.inventory.getStackInSlot(12);
                    if (isValidIndexCard(indexCardStack)) {
                        IItemHandler handler = cabinet.getCapabilityHandler(null);
                        if (handler != null) {
                            connectedHandlers.add(handler);
                            connectedCabinetPositions.add(cabinetPos.immutable());
                            cabinetCount++;
                        }
                    }
                }
            }
        }

        updateActivationLevel();
        scanNeeded = false;

        if (LOGGER.isDebugEnabled()) {
            double ms = (System.nanoTime() - startTime) / 1_000_000.0;
            LOGGER.debug("Full cabinet scan completed in {}ms ({} cabinets)", ms, connectedHandlers.size());
        }
    }

    private boolean isValidIndexCard(ItemStack stack) {
        return !stack.isEmpty() &&
                stack.getItem() instanceof IndexCardItem &&
                stack.get(ModDataComponents.COORDINATES) != null &&
                stack.get(ModDataComponents.COORDINATES).equals(getBlockPos());
    }

    private static List<BlockPos> getBoxPositions(BlockPos center, int radius) {
        int size = (radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1);
        List<BlockPos> positions = new ArrayList<>(size);

        int baseX = center.getX();
        int baseY = center.getY();
        int baseZ = center.getZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                }
            }
        }

        return positions;
    }

    private void updateActivationLevel() {
        int cabinetCount = connectedHandlers.size();

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

        if (stack.getItem() instanceof CapacityUpgradeItem) {
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

    /**
     * Get all BlockPos of cabinets connected to this index
     */
    public Set<BlockPos> getConnectedCabinetPositions() {
        if (scanNeeded) {
            performFullScan();
        }
        return Set.copyOf(connectedCabinetPositions);
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
            return stack.getItem() instanceof CapacityUpgradeItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) {
                updateRangeLevelVisual();
                updateCachedRange();
                invalidateCache(); // Range change requires a full rescan
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
        } else if (upgradeStack.getItem() instanceof CapacityUpgradeItem) {
            return 16;
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
            scanNeeded = true;
            updateRangeLevelVisual();
            updateCachedRange();
        }
    }
}
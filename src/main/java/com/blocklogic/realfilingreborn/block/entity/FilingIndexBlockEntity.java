package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    private Set<BlockPos> connectedCabinets = new HashSet<>();
    private final Map<Direction, IItemHandler> handlers = new HashMap<>();

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingIndexNetworkHandler(this, s));
    }

    public void updateNetworkCapabilities() {
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(getBlockPos());
        }
    }

    public boolean addConnectedCabinet(BlockPos cabinetPos) {
        if (connectedCabinets.contains(cabinetPos)) {
            return false;
        }

        if (level != null && level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
            if (cabinet.isInNetwork()) {
                return false;
            }

            connectedCabinets.add(cabinetPos);
            cabinet.setConnectedIndex(getBlockPos());
            setChanged();

            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                updateNetworkCapabilities();
                addCabinetToHandlerCaches(cabinetPos);
                updateBlockStateConnection();
            }

            return true;
        }

        return false;
    }

    public boolean removeConnectedCabinet(BlockPos cabinetPos) {
        boolean removed = connectedCabinets.remove(cabinetPos);

        if (removed) {
            if (level != null && level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                cabinet.clearConnectedIndex();
            }

            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                updateNetworkCapabilities();
                removeCabinetFromHandlerCaches(cabinetPos);
                updateBlockStateConnection();
            }
        }

        return removed;
    }

    public void disconnectAllCabinets() {
        if (level == null) return;

        // Create a copy of the set to avoid concurrent modification
        Set<BlockPos> cabinetsCopy = new HashSet<>(connectedCabinets);

        for (BlockPos cabinetPos : cabinetsCopy) {
            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                cabinet.clearConnectedIndex();
            }
        }

        // Clear the connections set
        connectedCabinets.clear();

        // Update network state
        updateNetworkCapabilities();
        invalidateHandlerCaches();

        setChanged();
    }

    private void updateBlockStateConnection() {
        if (level != null && !level.isClientSide() &&
                getBlockState().getBlock() instanceof FilingIndexBlock indexBlock) {
            boolean hasConnections = !connectedCabinets.isEmpty();
            indexBlock.updateConnectionState(level, getBlockPos(), hasConnections);
        }
    }

    private void addCabinetToHandlerCaches(BlockPos cabinetPos) {
        for (IItemHandler handler : handlers.values()) {
            if (handler instanceof FilingIndexNetworkHandler networkHandler) {
                networkHandler.addCabinet(cabinetPos);
            }
        }
    }

    private void removeCabinetFromHandlerCaches(BlockPos cabinetPos) {
        for (IItemHandler handler : handlers.values()) {
            if (handler instanceof FilingIndexNetworkHandler networkHandler) {
                networkHandler.removeCabinet(cabinetPos);
            }
        }
    }

    private void invalidateHandlerCaches() {
        for (IItemHandler handler : handlers.values()) {
            if (handler instanceof FilingIndexNetworkHandler networkHandler) {
                networkHandler.invalidateCache();
            }
        }
    }

    public boolean isInNetwork(BlockPos cabinetPos) {
        return connectedCabinets.contains(cabinetPos);
    }

    public void clearContents() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FilingIndexBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        if ((level.getGameTime() + pos.hashCode()) % 100 == 0) {
            blockEntity.cleanupBrokenConnections();
        }
    }

    private void cleanupBrokenConnections() {
        if (level == null) return;

        Iterator<BlockPos> iterator = connectedCabinets.iterator();
        boolean changed = false;
        List<BlockPos> removedCabinets = new ArrayList<>();

        while (iterator.hasNext()) {
            BlockPos cabinetPos = iterator.next();

            if (!level.isLoaded(cabinetPos) ||
                    !(level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity)) {
                iterator.remove();
                removedCabinets.add(cabinetPos);
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            updateNetworkCapabilities();

            for (BlockPos removedCabinet : removedCabinets) {
                removeCabinetFromHandlerCaches(removedCabinet);
            }

            updateBlockStateConnection();
        }
    }

    public List<FilingCabinetBlockEntity> getConnectedCabinetEntities() {
        if (level == null) return new ArrayList<>();

        return connectedCabinets.stream()
                .map(pos -> level.getBlockEntity(pos))
                .filter(be -> be instanceof FilingCabinetBlockEntity)
                .map(be -> (FilingCabinetBlockEntity) be)
                .collect(Collectors.toList());
    }

    public int getTotalNetworkSlots() {
        return getConnectedCabinetEntities().size() * 5; // 5 slots per cabinet
    }

    public int getUsedNetworkSlots() {
        return getConnectedCabinetEntities().stream()
                .mapToInt(cabinet -> {
                    int used = 0;
                    for (int i = 0; i < 5; i++) {
                        if (!cabinet.inventory.getStackInSlot(i).isEmpty()) {
                            used++;
                        }
                    }
                    return used;
                })
                .sum();
    }

    public List<ItemStack> findItemsInNetwork(Item targetItem) {
        List<ItemStack> found = new ArrayList<>();

        for (FilingCabinetBlockEntity cabinet : getConnectedCabinetEntities()) {
            for (int slot = 0; slot < 5; slot++) {
                ItemStack folderStack = cabinet.inventory.getStackInSlot(slot);
                if (!folderStack.isEmpty()) {
                    if (folderStack.getItem() instanceof FilingFolderItem) {
                        FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent()) {
                            Item storedItem = BuiltInRegistries.ITEM.get(contents.storedItemId().get());
                            if (storedItem == targetItem && contents.count() > 0) {
                                found.add(new ItemStack(storedItem, contents.count()));
                            }
                        }
                    }
                    else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                        NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                        if (contents != null && contents.storedItemId().isPresent()) {
                            Item storedItem = BuiltInRegistries.ITEM.get(contents.storedItemId().get());
                            if (storedItem == targetItem && !contents.storedItems().isEmpty()) {
                                for (NBTFilingFolderItem.SerializedItemStack serialized : contents.storedItems()) {
                                    found.add(serialized.stack().copy());
                                }
                            }
                        }
                    }
                }
            }
        }

        return found;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());

        for (int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        ListTag cabinetList = new ListTag();
        for (BlockPos pos : connectedCabinets) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            cabinetList.add(posTag);
        }
        tag.put("connectedCabinets", cabinetList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        connectedCabinets.clear();
        ListTag cabinetList = tag.getList("connectedCabinets", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cabinetList.size(); i++) {
            CompoundTag posTag = cabinetList.getCompound(i);
            BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
            );
            connectedCabinets.add(pos);
        }

        invalidateHandlerCaches();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.realfilingreborn.filing_index_menu_title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new FilingIndexMenu(i, inventory, this);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private static class FilingIndexNetworkHandler implements IItemHandler {
        private final FilingIndexBlockEntity indexEntity;
        private final Direction side;
        private int cachedTotalSlots = -1;
        private List<CabinetSlotMapping> cachedSlotMappings = null;

        public FilingIndexNetworkHandler(FilingIndexBlockEntity indexEntity, @Nullable Direction side) {
            this.indexEntity = indexEntity;
            this.side = side;
        }

        // Direct cabinet addition - O(1) operation
        public void addCabinet(BlockPos cabinetPos) {
            if (cachedSlotMappings == null) {
                return;
            }

            if (indexEntity.level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                IItemHandler cabinetHandler = cabinet.getCapabilityHandler(side);
                if (cabinetHandler != null) {
                    int cabinetSlots = cabinetHandler.getSlots();
                    for (int i = 0; i < cabinetSlots; i++) {
                        cachedSlotMappings.add(new CabinetSlotMapping(cabinetHandler, i, cabinetPos));
                    }
                    cachedTotalSlots += cabinetSlots;
                }
            }
        }

        public void removeCabinet(BlockPos cabinetPos) {
            if (cachedSlotMappings == null) {
                return;
            }

            int removedSlots = 0;
            Iterator<CabinetSlotMapping> iterator = cachedSlotMappings.iterator();
            while (iterator.hasNext()) {
                CabinetSlotMapping mapping = iterator.next();
                if (mapping.cabinetPos.equals(cabinetPos)) {
                    iterator.remove();
                    removedSlots++;
                }
            }

            cachedTotalSlots -= removedSlots;
        }

        public void invalidateCache() {
            cachedTotalSlots = -1;
            cachedSlotMappings = null;
        }

        private void buildSlotMappings() {
            if (cachedSlotMappings != null) return;

            cachedSlotMappings = new ArrayList<>();
            cachedTotalSlots = 0;

            for (BlockPos cabinetPos : indexEntity.connectedCabinets) {
                if (indexEntity.level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    IItemHandler cabinetHandler = cabinet.getCapabilityHandler(side);
                    if (cabinetHandler != null) {
                        int cabinetSlots = cabinetHandler.getSlots();
                        for (int i = 0; i < cabinetSlots; i++) {
                            cachedSlotMappings.add(new CabinetSlotMapping(cabinetHandler, i, cabinetPos));
                        }
                        cachedTotalSlots += cabinetSlots;
                    }
                }
            }
        }

        @Override
        public int getSlots() {
            if (cachedTotalSlots == -1) {
                buildSlotMappings();
            }
            return cachedTotalSlots;
        }

        private CabinetSlotMapping getCabinetAndSlot(int globalSlot) {
            buildSlotMappings();

            if (globalSlot < 0 || globalSlot >= cachedSlotMappings.size()) {
                return null;
            }

            return cachedSlotMappings.get(globalSlot);
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            CabinetSlotMapping mapping = getCabinetAndSlot(slot);
            if (mapping == null) return ItemStack.EMPTY;

            return mapping.handler.getStackInSlot(mapping.localSlot);
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;

            buildSlotMappings();

            for (CabinetSlotMapping mapping : cachedSlotMappings) {
                ItemStack remaining = mapping.handler.insertItem(mapping.localSlot, stack, simulate);
                if (remaining.getCount() < stack.getCount()) {
                    return remaining;
                }
            }
            return stack;
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            CabinetSlotMapping mapping = getCabinetAndSlot(slot);
            if (mapping == null) return ItemStack.EMPTY;

            return mapping.handler.extractItem(mapping.localSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            CabinetSlotMapping mapping = getCabinetAndSlot(slot);
            if (mapping == null) return 0;

            return mapping.handler.getSlotLimit(mapping.localSlot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            CabinetSlotMapping mapping = getCabinetAndSlot(slot);
            if (mapping == null) return false;

            return mapping.handler.isItemValid(mapping.localSlot, stack);
        }

        private record CabinetSlotMapping(IItemHandler handler, int localSlot, BlockPos cabinetPos) {}
    }
}
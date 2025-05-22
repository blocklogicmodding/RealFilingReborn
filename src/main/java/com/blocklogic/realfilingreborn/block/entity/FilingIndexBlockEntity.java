package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    private Set<BlockPos> connectedCabinets = new HashSet<>();

    public boolean addConnectedCabinet(BlockPos cabinetPos) {
        if (connectedCabinets.contains(cabinetPos)) {
            return false;
        }

        if (level != null && level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity) {
            connectedCabinets.add(cabinetPos);
            setChanged();

            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }

            return true;
        }

        return false;
    }

    public boolean removeConnectedCabinet(BlockPos cabinetPos) {
        boolean removed = connectedCabinets.remove(cabinetPos);

        if (removed) {
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        return removed;
    }

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

    public void clearContents() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FilingIndexBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        // Clean up broken cabinet connections every 100 ticks (5 seconds)
        if (level.getGameTime() % 100 == 0) {
            blockEntity.cleanupBrokenConnections();
        }
    }

    // NEW: Cleanup method for broken cabinet connections
    private void cleanupBrokenConnections() {
        if (level == null) return;

        Iterator<BlockPos> iterator = connectedCabinets.iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            BlockPos cabinetPos = iterator.next();

            // Check if cabinet still exists and is valid
            if (!level.isLoaded(cabinetPos) ||
                    !(level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity)) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
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
}

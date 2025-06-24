package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.capability.FilingIndexFluidHandler;
import com.blocklogic.realfilingreborn.capability.FilingIndexItemHandler;
import com.blocklogic.realfilingreborn.item.custom.DiamondRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.IronRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.NetheriteRangeUpgrade;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FilingIndexBlockEntity extends BlockEntity implements MenuProvider {
    private final Set<BlockPos> linkedCabinets = new HashSet<>();
    private final Map<Direction, IItemHandler> handlers = new HashMap<>();
    private final Map<Direction, IFluidHandler> fluidHandlers = new HashMap<>();

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();

            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    @Nullable
    public IItemHandler getCapabilityHandler(@Nullable Direction side) {
        return handlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingIndexItemHandler(this));
    }

    @Nullable
    public IFluidHandler getFluidCapabilityHandler(@Nullable Direction side) {
        return fluidHandlers.computeIfAbsent(side != null ? side : Direction.UP, s -> new FilingIndexFluidHandler(this));
    }

    public void drops() {
        // Clear all linked cabinets first
        clearAllLinkedCabinets();

        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    public void addCabinet(BlockPos cabinetPos) {
        linkedCabinets.add(cabinetPos);
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void removeCabinet(BlockPos cabinetPos) {
        linkedCabinets.remove(cabinetPos);
        setChanged();

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public Set<BlockPos> getLinkedCabinets() {
        return new HashSet<>(linkedCabinets);
    }

    public int getLinkedCabinetCount() {
        return linkedCabinets.size();
    }

    public void clearAllLinkedCabinets() {
        if (level != null && !level.isClientSide()) {
            for (BlockPos cabinetPos : linkedCabinets) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    cabinet.clearControllerPos();
                } else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    fluidCabinet.clearControllerPos();
                }
            }
        }
        linkedCabinets.clear();
        setChanged();
    }

    public int getRange() {
        ItemStack upgradeStack = inventory.getStackInSlot(0);
        if (upgradeStack.isEmpty()) {
            return 8; // Base range
        }

        if (upgradeStack.getItem() instanceof NetheriteRangeUpgrade) {
            return 64;
        } else if (upgradeStack.getItem() instanceof DiamondRangeUpgrade) {
            return 32;
        } else if (upgradeStack.getItem() instanceof IronRangeUpgrade) {
            return 16;
        }

        return 8; // Fallback
    }

    public boolean removeCabinetAt(BlockPos cabinetPos) {
        if (linkedCabinets.remove(cabinetPos)) {
            if (level != null && !level.isClientSide()) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    cabinet.clearControllerPos();
                } else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet) {
                    fluidCabinet.clearControllerPos();
                }
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));

        // Save linked cabinets
        ListTag cabinetList = new ListTag();
        for (BlockPos cabinetPos : linkedCabinets) {
            cabinetList.add(LongTag.valueOf(cabinetPos.asLong()));
        }
        tag.put("linkedCabinets", cabinetList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));

        // Load linked cabinets
        linkedCabinets.clear();
        if (tag.contains("linkedCabinets")) {
            ListTag cabinetList = tag.getList("linkedCabinets", 4);
            for (int i = 0; i < cabinetList.size(); i++) {
                long posLong = ((LongTag) cabinetList.get(i)).getAsLong();
                BlockPos cabinetPos = BlockPos.of(posLong);
                linkedCabinets.add(cabinetPos);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("blockentity.realfilingreborn.filing_index_name");
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
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }
}
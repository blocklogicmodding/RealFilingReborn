package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.component.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public class FilingIndexBlockEntity extends BlockEntity {

    public FilingIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILING_INDEX_BE.get(), pos, blockState);
    }

    public List<IItemHandler> getCabinetItemHandlers() {
        List<IItemHandler> handlers = new ArrayList<>();

        if (level != null && !level.isClientSide()) {
            int radius = 16;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos cabinetPos = getBlockPos().offset(x, y, z);

                        if (level.isLoaded(cabinetPos)) {
                            BlockEntity be = level.getBlockEntity(cabinetPos);
                            if (be instanceof FilingCabinetBlockEntity cabinet) {
                                ItemStack indexCardStack = cabinet.inventory.getStackInSlot(10);
                                if (!indexCardStack.isEmpty() &&
                                        indexCardStack.get(ModDataComponents.COORDINATES) != null) {

                                    BlockPos linkedPos = indexCardStack.get(ModDataComponents.COORDINATES);
                                    if (linkedPos.equals(getBlockPos())) {
                                        IItemHandler handler = cabinet.getCapabilityHandler(null);
                                        if (handler != null) {
                                            handlers.add(handler);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return handlers;
    }

    public int getCabinetCount() {
        return getCabinetItemHandlers().size();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
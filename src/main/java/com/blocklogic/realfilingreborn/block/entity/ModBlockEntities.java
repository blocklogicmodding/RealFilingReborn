package com.blocklogic.realfilingreborn.block.entity;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealFilingReborn.MODID);

    public static final Supplier<BlockEntityType<FilingCabinetBlockEntity>> FILING_CABINET_BE = BLOCK_ENTITIES.register("filing_cabinet_be", () -> BlockEntityType.Builder.of(
            FilingCabinetBlockEntity::new, ModBlocks.FILING_CABINET.get()).build(null));

    public static final Supplier<BlockEntityType<FluidCabinetBlockEntity>> FLUID_CABINET_BE = BLOCK_ENTITIES.register("fluid_cabinet_be", () -> BlockEntityType.Builder.of(
            FluidCabinetBlockEntity::new, ModBlocks.FLUID_CABINET.get()).build(null));

    public static void register (IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

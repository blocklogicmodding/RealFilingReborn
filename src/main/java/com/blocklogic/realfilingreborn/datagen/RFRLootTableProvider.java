package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

public class RFRLootTableProvider extends BlockLootSubProvider {
    protected RFRLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.FILING_CABINET.get());
        dropSelf(ModBlocks.FLUID_CABINET.get());
        dropSelf(ModBlocks.FILING_INDEX.get());
        dropSelf(ModBlocks.INDEX_CABLE_CORE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}

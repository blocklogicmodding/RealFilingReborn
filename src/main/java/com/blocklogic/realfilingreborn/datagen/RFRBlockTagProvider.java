package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.util.RFRTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class RFRBlockTagProvider extends BlockTagsProvider {
    public RFRBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, RealFilingReborn.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.FILING_CABINET.get())
                .add(ModBlocks.FLUID_CABINET.get())
                .add(ModBlocks.FILING_INDEX.get())
                .add(ModBlocks.INDEX_CABLE_CORE.get());

        tag(RFRTags.Blocks.CABINETS)
                .add(ModBlocks.FILING_CABINET.get())
                .add(ModBlocks.FLUID_CABINET.get());

        tag(RFRTags.Blocks.FILING_CONTROLLERS)
                .add(ModBlocks.FILING_INDEX.get());

        tag(RFRTags.Blocks.INDEX_CABLES)
                .add(ModBlocks.INDEX_CABLE_CORE.get());
    }
}

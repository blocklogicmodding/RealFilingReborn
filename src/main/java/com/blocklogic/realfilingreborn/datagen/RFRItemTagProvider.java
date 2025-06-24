package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.item.ModItems;
import com.blocklogic.realfilingreborn.util.RFRTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class RFRItemTagProvider extends ItemTagsProvider {
    public RFRItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, RealFilingReborn.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(RFRTags.Items.FOLDERS)
                .add(ModItems.FILING_FOLDER.get())
                .add(ModItems.NBT_FILING_FOLDER.get())
                .add(ModItems.FLUID_CANISTER.get());

        tag(RFRTags.Items.ARCHIVE_TOOLS)
                .add(ModItems.ERASER.get())
                .add(ModItems.CABINET_CONVERSION_KIT.get())
                .add(ModItems.LEDGER.get());

        tag(RFRTags.Items.RANGE_UPGRADES)
                .add(ModItems.IRON_RANGE_UPGRADE.get())
                .add(ModItems.DIAMOND_RANGE_UPGRADE.get())
                .add(ModItems.NETHERITE_RANGE_UPGRADE.get());
    }
}

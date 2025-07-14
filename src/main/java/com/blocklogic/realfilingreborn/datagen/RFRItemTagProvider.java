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
                .add(ModItems.FLUID_CANISTER.get())

                .add(ModItems.WHITE_FILING_FOLDER.get())
                .add(ModItems.ORANGE_FILING_FOLDER.get())
                .add(ModItems.MAGENTA_FILING_FOLDER.get())
                .add(ModItems.LIGHT_BLUE_FILING_FOLDER.get())
                .add(ModItems.YELLOW_FILING_FOLDER.get())
                .add(ModItems.LIME_FILING_FOLDER.get())
                .add(ModItems.PINK_FILING_FOLDER.get())
                .add(ModItems.GRAY_FILING_FOLDER.get())
                .add(ModItems.LIGHT_GRAY_FILING_FOLDER.get())
                .add(ModItems.CYAN_FILING_FOLDER.get())
                .add(ModItems.PURPLE_FILING_FOLDER.get())
                .add(ModItems.BLUE_FILING_FOLDER.get())
                .add(ModItems.BROWN_FILING_FOLDER.get())
                .add(ModItems.GREEN_FILING_FOLDER.get())
                .add(ModItems.RED_FILING_FOLDER.get())
                .add(ModItems.BLACK_FILING_FOLDER.get())

                .add(ModItems.WHITE_NBT_FILING_FOLDER.get())
                .add(ModItems.ORANGE_NBT_FILING_FOLDER.get())
                .add(ModItems.MAGENTA_NBT_FILING_FOLDER.get())
                .add(ModItems.LIGHT_BLUE_NBT_FILING_FOLDER.get())
                .add(ModItems.YELLOW_NBT_FILING_FOLDER.get())
                .add(ModItems.LIME_NBT_FILING_FOLDER.get())
                .add(ModItems.PINK_NBT_FILING_FOLDER.get())
                .add(ModItems.GRAY_NBT_FILING_FOLDER.get())
                .add(ModItems.LIGHT_GRAY_NBT_FILING_FOLDER.get())
                .add(ModItems.CYAN_NBT_FILING_FOLDER.get())
                .add(ModItems.PURPLE_NBT_FILING_FOLDER.get())
                .add(ModItems.BLUE_NBT_FILING_FOLDER.get())
                .add(ModItems.BROWN_NBT_FILING_FOLDER.get())
                .add(ModItems.GREEN_NBT_FILING_FOLDER.get())
                .add(ModItems.RED_NBT_FILING_FOLDER.get())
                .add(ModItems.BLACK_NBT_FILING_FOLDER.get());

        tag(RFRTags.Items.ARCHIVE_TOOLS)
                .add(ModItems.ERASER.get())
                .add(ModItems.CABINET_CONVERSION_KIT.get())
                .add(ModItems.LEDGER.get());

        tag(RFRTags.Items.RANGE_UPGRADES)
                .add(ModItems.IRON_RANGE_UPGRADE.get())
                .add(ModItems.DIAMOND_RANGE_UPGRADE.get())
                .add(ModItems.NETHERITE_RANGE_UPGRADE.get());

        tag(RFRTags.Items.CABINET_AS_ITEM)
                .add(ModBlocks.FILING_CABINET.asItem())
                .add(ModBlocks.FLUID_CABINET.asItem());

        tag(RFRTags.Items.DYED_FOLDERS)
                .add(ModItems.WHITE_FILING_FOLDER.get())
                .add(ModItems.ORANGE_FILING_FOLDER.get())
                .add(ModItems.MAGENTA_FILING_FOLDER.get())
                .add(ModItems.LIGHT_BLUE_FILING_FOLDER.get())
                .add(ModItems.YELLOW_FILING_FOLDER.get())
                .add(ModItems.LIME_FILING_FOLDER.get())
                .add(ModItems.PINK_FILING_FOLDER.get())
                .add(ModItems.GRAY_FILING_FOLDER.get())
                .add(ModItems.LIGHT_GRAY_FILING_FOLDER.get())
                .add(ModItems.CYAN_FILING_FOLDER.get())
                .add(ModItems.PURPLE_FILING_FOLDER.get())
                .add(ModItems.BLUE_FILING_FOLDER.get())
                .add(ModItems.BROWN_FILING_FOLDER.get())
                .add(ModItems.GREEN_FILING_FOLDER.get())
                .add(ModItems.RED_FILING_FOLDER.get())
                .add(ModItems.BLACK_FILING_FOLDER.get())

                .add(ModItems.WHITE_NBT_FILING_FOLDER.get())
                .add(ModItems.ORANGE_NBT_FILING_FOLDER.get())
                .add(ModItems.MAGENTA_NBT_FILING_FOLDER.get())
                .add(ModItems.LIGHT_BLUE_NBT_FILING_FOLDER.get())
                .add(ModItems.YELLOW_NBT_FILING_FOLDER.get())
                .add(ModItems.LIME_NBT_FILING_FOLDER.get())
                .add(ModItems.PINK_NBT_FILING_FOLDER.get())
                .add(ModItems.GRAY_NBT_FILING_FOLDER.get())
                .add(ModItems.LIGHT_GRAY_NBT_FILING_FOLDER.get())
                .add(ModItems.CYAN_NBT_FILING_FOLDER.get())
                .add(ModItems.PURPLE_NBT_FILING_FOLDER.get())
                .add(ModItems.BLUE_NBT_FILING_FOLDER.get())
                .add(ModItems.BROWN_NBT_FILING_FOLDER.get())
                .add(ModItems.GREEN_NBT_FILING_FOLDER.get())
                .add(ModItems.RED_NBT_FILING_FOLDER.get())
                .add(ModItems.BLACK_NBT_FILING_FOLDER.get());
    }
}

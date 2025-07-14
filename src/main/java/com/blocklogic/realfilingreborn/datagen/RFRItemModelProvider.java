package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.item.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class RFRItemModelProvider extends ItemModelProvider {
    public RFRItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, RealFilingReborn.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.FILING_FOLDER.get());
        basicItem(ModItems.NBT_FILING_FOLDER.get());
        basicItem(ModItems.FLUID_CANISTER.get());
        basicItem(ModItems.ERASER.get());
        basicItem(ModItems.CABINET_CONVERSION_KIT.get());
        basicItem(ModItems.LEDGER.get());
        basicItem(ModItems.IRON_RANGE_UPGRADE.get());
        basicItem(ModItems.DIAMOND_RANGE_UPGRADE.get());
        basicItem(ModItems.NETHERITE_RANGE_UPGRADE.get());

        // Dyed Filing Folders
        basicItem(ModItems.WHITE_FILING_FOLDER.get());
        basicItem(ModItems.ORANGE_FILING_FOLDER.get());
        basicItem(ModItems.MAGENTA_FILING_FOLDER.get());
        basicItem(ModItems.LIGHT_BLUE_FILING_FOLDER.get());
        basicItem(ModItems.YELLOW_FILING_FOLDER.get());
        basicItem(ModItems.LIME_FILING_FOLDER.get());
        basicItem(ModItems.PINK_FILING_FOLDER.get());
        basicItem(ModItems.GRAY_FILING_FOLDER.get());
        basicItem(ModItems.LIGHT_GRAY_FILING_FOLDER.get());
        basicItem(ModItems.CYAN_FILING_FOLDER.get());
        basicItem(ModItems.PURPLE_FILING_FOLDER.get());
        basicItem(ModItems.BLUE_FILING_FOLDER.get());
        basicItem(ModItems.BROWN_FILING_FOLDER.get());
        basicItem(ModItems.GREEN_FILING_FOLDER.get());
        basicItem(ModItems.RED_FILING_FOLDER.get());
        basicItem(ModItems.BLACK_FILING_FOLDER.get());

        // Dyed NBT Filing Folders
        basicItem(ModItems.WHITE_NBT_FILING_FOLDER.get());
        basicItem(ModItems.ORANGE_NBT_FILING_FOLDER.get());
        basicItem(ModItems.MAGENTA_NBT_FILING_FOLDER.get());
        basicItem(ModItems.LIGHT_BLUE_NBT_FILING_FOLDER.get());
        basicItem(ModItems.YELLOW_NBT_FILING_FOLDER.get());
        basicItem(ModItems.LIME_NBT_FILING_FOLDER.get());
        basicItem(ModItems.PINK_NBT_FILING_FOLDER.get());
        basicItem(ModItems.GRAY_NBT_FILING_FOLDER.get());
        basicItem(ModItems.LIGHT_GRAY_NBT_FILING_FOLDER.get());
        basicItem(ModItems.CYAN_NBT_FILING_FOLDER.get());
        basicItem(ModItems.PURPLE_NBT_FILING_FOLDER.get());
        basicItem(ModItems.BLUE_NBT_FILING_FOLDER.get());
        basicItem(ModItems.BROWN_NBT_FILING_FOLDER.get());
        basicItem(ModItems.GREEN_NBT_FILING_FOLDER.get());
        basicItem(ModItems.RED_NBT_FILING_FOLDER.get());
        basicItem(ModItems.BLACK_NBT_FILING_FOLDER.get());
    }
}

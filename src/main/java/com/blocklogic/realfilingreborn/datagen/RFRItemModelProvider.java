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
    }
}

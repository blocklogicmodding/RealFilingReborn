package com.blocklogic.realfilingreborn.item;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealFilingReborn.MODID);

    public static final Supplier<CreativeModeTab> REAL_FILING_REBORN = CREATIVE_MODE_TAB.register("real_filing_reborn_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.FILING_FOLDER.get()))
                    .title(Component.translatable("creativetab.realfilingreborn.real_filing_reborn"))
                    .displayItems((ItemDisplayParameters, output) -> {
                        output.accept(ModBlocks.FILING_CABINET);
                        output.accept(ModBlocks.FLUID_CABINET);
                        output.accept(ModBlocks.FILING_INDEX);

                        output.accept(ModItems.FILING_FOLDER);
                        output.accept(ModItems.NBT_FILING_FOLDER);
                        output.accept(ModItems.ERASER);
                        output.accept(ModItems.FLUID_CANISTER);
                        output.accept(ModItems.CABINET_CONVERSION_KIT);
                        output.accept(ModItems.LEDGER);
                        output.accept(ModItems.IRON_RANGE_UPGRADE);
                        output.accept(ModItems.DIAMOND_RANGE_UPGRADE);
                        output.accept(ModItems.NETHERITE_RANGE_UPGRADE);
                    }).build());

    public static final Supplier<CreativeModeTab> REAL_FILING_REBORN_DYED_FOLDERS = CREATIVE_MODE_TAB.register("real_filing_reborn_dyed_folders_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.PINK_FILING_FOLDER.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "real_filing_reborn"))
                    .title(Component.translatable("creativetab.realfilingreborn.real_filing_reborn_dyed_folder"))
                    .displayItems((ItemDisplayParameters, output) -> {
                        output.accept(ModItems.WHITE_FILING_FOLDER);
                        output.accept(ModItems.ORANGE_FILING_FOLDER);
                        output.accept(ModItems.MAGENTA_FILING_FOLDER);
                        output.accept(ModItems.LIGHT_BLUE_FILING_FOLDER);
                        output.accept(ModItems.YELLOW_FILING_FOLDER);
                        output.accept(ModItems.LIME_FILING_FOLDER);
                        output.accept(ModItems.PINK_FILING_FOLDER);
                        output.accept(ModItems.GRAY_FILING_FOLDER);
                        output.accept(ModItems.LIGHT_GRAY_FILING_FOLDER);
                        output.accept(ModItems.CYAN_FILING_FOLDER);
                        output.accept(ModItems.PURPLE_FILING_FOLDER);
                        output.accept(ModItems.BLUE_FILING_FOLDER);
                        output.accept(ModItems.BROWN_FILING_FOLDER);
                        output.accept(ModItems.GREEN_FILING_FOLDER);
                        output.accept(ModItems.RED_FILING_FOLDER);
                        output.accept(ModItems.BLACK_FILING_FOLDER);

                        output.accept(ModItems.WHITE_NBT_FILING_FOLDER);
                        output.accept(ModItems.ORANGE_NBT_FILING_FOLDER);
                        output.accept(ModItems.MAGENTA_NBT_FILING_FOLDER);
                        output.accept(ModItems.LIGHT_BLUE_NBT_FILING_FOLDER);
                        output.accept(ModItems.YELLOW_NBT_FILING_FOLDER);
                        output.accept(ModItems.LIME_NBT_FILING_FOLDER);
                        output.accept(ModItems.PINK_NBT_FILING_FOLDER);
                        output.accept(ModItems.GRAY_NBT_FILING_FOLDER);
                        output.accept(ModItems.LIGHT_GRAY_NBT_FILING_FOLDER);
                        output.accept(ModItems.CYAN_NBT_FILING_FOLDER);
                        output.accept(ModItems.PURPLE_NBT_FILING_FOLDER);
                        output.accept(ModItems.BLUE_NBT_FILING_FOLDER);
                        output.accept(ModItems.BROWN_NBT_FILING_FOLDER);
                        output.accept(ModItems.GREEN_NBT_FILING_FOLDER);
                        output.accept(ModItems.RED_NBT_FILING_FOLDER);
                        output.accept(ModItems.BLACK_NBT_FILING_FOLDER);
                    }).build());

    public static void register (IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}

package com.blocklogic.realfilingreborn.item;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealFilingReborn.MODID);

    public static final Supplier<CreativeModeTab> REAL_FILING_REBORN = CREATIVE_MODE_TAB.register("real_filing_reborn",
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
                    }).build());

    public static void register (IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}

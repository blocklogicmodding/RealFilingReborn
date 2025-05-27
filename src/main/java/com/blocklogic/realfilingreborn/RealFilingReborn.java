package com.blocklogic.realfilingreborn;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.block.entity.ModBlockEntities;
import com.blocklogic.realfilingreborn.block.entity.renderer.FilingCabinetBlockEntityRenderer;
import com.blocklogic.realfilingreborn.block.entity.renderer.FluidCabinetBlockEntityRenderer;
import com.blocklogic.realfilingreborn.item.ModCreativeModTab;
import com.blocklogic.realfilingreborn.item.ModItems;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.screen.ModMenuTypes;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetScreen;
import com.blocklogic.realfilingreborn.screen.custom.FluidCabinetScreen;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(RealFilingReborn.MODID)
public class RealFilingReborn
{
    public static final String MODID = "realfilingreborn";

    private static final Logger LOGGER = LogUtils.getLogger();

    public RealFilingReborn(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModTab.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        FilingFolderItem.DATA_COMPONENTS.register(modEventBus);
        NBTFilingFolderItem.DATA_COMPONENTS.register(modEventBus);
        FluidCanisterItem.DATA_COMPONENTS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FILING_CABINET_BE.get(),
                (filingCabinetBE, side) -> filingCabinetBE.getCapabilityHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FLUID_CABINET_BE.get(),
                (fluidCabinetBE, side) -> fluidCabinetBE.getCapabilityHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_CABINET_BE.get(),
                (fluidCabinetBE, side) -> fluidCabinetBE.getFluidCapabilityHandler(side)
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {

    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
        }

        @SubscribeEvent
        public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.FILING_CABINET_BE.get(),
                    FilingCabinetBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.FLUID_CABINET_BE.get(),
                    FluidCabinetBlockEntityRenderer::new);
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.FILING_CABINET_MENU.get(), FilingCabinetScreen::new);
            event.register(ModMenuTypes.FLUID_CABINET_MENU.get(), FluidCabinetScreen::new);
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    ModBlockEntities.FILING_CABINET_BE.get(),
                    (filingCabinetBE, side) -> filingCabinetBE.getCapabilityHandler(side)
            );

            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    ModBlockEntities.FLUID_CABINET_BE.get(),
                    (fluidCabinetBE, side) -> fluidCabinetBE.getCapabilityHandler(side)
            );
        }
    }
}
package com.blocklogic.realfilingreborn.screen;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.screen.custom.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RealFilingReborn.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FilingCabinetMenu>> FILING_CABINET_MENU =
            registerMenuType("filing_cabinet_menu", FilingCabinetMenu::new);

    public static final DeferredHolder<MenuType<?>, MenuType<FluidCabinetMenu>> FLUID_CABINET_MENU =
            registerMenuType("fluid_cabinet_menu", FluidCabinetMenu::new);

    public static final DeferredHolder<MenuType<?>, MenuType<FilingIndexMenu>> FILING_INDEX_MENU =
            registerMenuType("filing_index_menu", FilingIndexMenu::new);

    public static final DeferredHolder<MenuType<?>, MenuType<FilingFolderMenu>> FILING_FOLDER_MENU =
            registerMenuType("filing_folder_menu", FilingFolderMenu::new);

    public static final DeferredHolder<MenuType<?>, MenuType<FluidCanisterMenu>> FLUID_CANISTER_MENU =
            registerMenuType("fluid_canister_menu", FluidCanisterMenu::new);

    private static <T extends AbstractContainerMenu>DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register (IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}

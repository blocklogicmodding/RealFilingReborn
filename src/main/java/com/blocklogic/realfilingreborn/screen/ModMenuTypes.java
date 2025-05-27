package com.blocklogic.realfilingreborn.screen;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.screen.custom.FilingCabinetMenu;
import com.blocklogic.realfilingreborn.screen.custom.FilingIndexMenu;
import com.blocklogic.realfilingreborn.screen.custom.FluidCabinetMenu;
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

    private static <T extends AbstractContainerMenu>DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register (IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}

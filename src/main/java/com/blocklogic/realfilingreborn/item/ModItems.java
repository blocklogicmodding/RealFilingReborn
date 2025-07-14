package com.blocklogic.realfilingreborn.item;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.item.custom.*;
import com.blocklogic.realfilingreborn.item.custom.DiamondRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.IronRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.NetheriteRangeUpgrade;
import com.blocklogic.realfilingreborn.item.custom.dyed_folders.*;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealFilingReborn.MODID);

    public static final DeferredItem<Item> FILING_FOLDER = ITEMS.register("filing_folder",
            () -> new FilingFolderItem(new Item.Properties()));

    public static final DeferredItem<Item> NBT_FILING_FOLDER = ITEMS.register("nbt_filing_folder",
            () -> new NBTFilingFolderItem(new Item.Properties()));

    public static final DeferredItem<Item> ERASER = ITEMS.register("eraser",
            () -> new EraserItem(new Item.Properties().durability(64)));

    public static final DeferredItem<Item> FLUID_CANISTER = ITEMS.register("fluid_canister",
            () -> new FluidCanisterItem(new Item.Properties()));

    public static final DeferredItem<Item> CABINET_CONVERSION_KIT = ITEMS.register("cabinet_conversion_kit",
            () -> new CabinetConversionItem(new Item.Properties()));

    public static final DeferredItem<Item> IRON_RANGE_UPGRADE = ITEMS.register("iron_range_upgrade",
            () -> new IronRangeUpgrade(new Item.Properties()));

    public static final DeferredItem<Item> DIAMOND_RANGE_UPGRADE = ITEMS.register("diamond_range_upgrade",
            () -> new DiamondRangeUpgrade(new Item.Properties()));

    public static final DeferredItem<Item> NETHERITE_RANGE_UPGRADE = ITEMS.register("netherite_range_upgrade",
            () -> new NetheriteRangeUpgrade(new Item.Properties()));

    public static final DeferredItem<Item> LEDGER = ITEMS.register("ledger",
            () -> new LedgerItem(new Item.Properties()));

    //Dyed Folders
    public static final DeferredItem<Item> WHITE_FILING_FOLDER = ITEMS.register("white_filing_folder",
            () -> new WhiteFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> ORANGE_FILING_FOLDER = ITEMS.register("orange_filing_folder",
            () -> new OrangeFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> MAGENTA_FILING_FOLDER = ITEMS.register("magenta_filing_folder",
            () -> new MagentaFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_BLUE_FILING_FOLDER = ITEMS.register("light_blue_filing_folder",
            () -> new LightBlueFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> YELLOW_FILING_FOLDER = ITEMS.register("yellow_filing_folder",
            () -> new YellowFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIME_FILING_FOLDER = ITEMS.register("lime_filing_folder",
            () -> new LimeFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> PINK_FILING_FOLDER = ITEMS.register("pink_filing_folder",
            () -> new PinkFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> GRAY_FILING_FOLDER = ITEMS.register("gray_filing_folder",
            () -> new GrayFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_GRAY_FILING_FOLDER = ITEMS.register("light_gray_filing_folder",
            () -> new LightGrayFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> CYAN_FILING_FOLDER = ITEMS.register("cyan_filing_folder",
            () -> new CyanFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> PURPLE_FILING_FOLDER = ITEMS.register("purple_filing_folder",
            () -> new PurpleFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BLUE_FILING_FOLDER = ITEMS.register("blue_filing_folder",
            () -> new BlueFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BROWN_FILING_FOLDER = ITEMS.register("brown_filing_folder",
            () -> new BrownFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> GREEN_FILING_FOLDER = ITEMS.register("green_filing_folder",
            () -> new GreenFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> RED_FILING_FOLDER = ITEMS.register("red_filing_folder",
            () -> new RedFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BLACK_FILING_FOLDER = ITEMS.register("black_filing_folder",
            () -> new BlackFilingFolder(new Item.Properties()));

    //Dyed NBT Folders
    public static final DeferredItem<Item> WHITE_NBT_FILING_FOLDER = ITEMS.register("white_nbt_filing_folder",
            () -> new WhiteNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> ORANGE_NBT_FILING_FOLDER = ITEMS.register("orange_nbt_filing_folder",
            () -> new OrangeNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> MAGENTA_NBT_FILING_FOLDER = ITEMS.register("magenta_nbt_filing_folder",
            () -> new MagentaNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_BLUE_NBT_FILING_FOLDER = ITEMS.register("light_blue_nbt_filing_folder",
            () -> new LightBlueNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> YELLOW_NBT_FILING_FOLDER = ITEMS.register("yellow_nbt_filing_folder",
            () -> new YellowNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIME_NBT_FILING_FOLDER = ITEMS.register("lime_nbt_filing_folder",
            () -> new LimeNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> PINK_NBT_FILING_FOLDER = ITEMS.register("pink_nbt_filing_folder",
            () -> new PinkNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> GRAY_NBT_FILING_FOLDER = ITEMS.register("gray_nbt_filing_folder",
            () -> new GrayNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_GRAY_NBT_FILING_FOLDER = ITEMS.register("light_gray_nbt_filing_folder",
            () -> new LightGrayNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> CYAN_NBT_FILING_FOLDER = ITEMS.register("cyan_nbt_filing_folder",
            () -> new CyanNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> PURPLE_NBT_FILING_FOLDER = ITEMS.register("purple_nbt_filing_folder",
            () -> new PurpleNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BLUE_NBT_FILING_FOLDER = ITEMS.register("blue_nbt_filing_folder",
            () -> new BlueNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BROWN_NBT_FILING_FOLDER = ITEMS.register("brown_nbt_filing_folder",
            () -> new BrownNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> GREEN_NBT_FILING_FOLDER = ITEMS.register("green_nbt_filing_folder",
            () -> new GreenNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> RED_NBT_FILING_FOLDER = ITEMS.register("red_nbt_filing_folder",
            () -> new RedNBTFilingFolder(new Item.Properties()));

    public static final DeferredItem<Item> BLACK_NBT_FILING_FOLDER = ITEMS.register("black_nbt_filing_folder",
            () -> new BlackNBTFilingFolder(new Item.Properties()));


    public static void register (IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

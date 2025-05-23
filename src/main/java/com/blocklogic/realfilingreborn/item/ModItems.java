package com.blocklogic.realfilingreborn.item;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.item.custom.*;
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

    public static final DeferredItem<Item> LEDGER = ITEMS.register("ledger",
            () -> new LedgerItem(new Item.Properties()));

    public static final DeferredItem<Item> INDEX_UPGRADE_IRON = ITEMS.register("index_range_upgrade_iron",
            () -> new IndexRangeUpgradeIronItem(new Item.Properties()));

    public static final DeferredItem<Item> INDEX_UPGRADE_DIAMOND = ITEMS.register("index_range_upgrade_diamond",
            () -> new IndexRangeUpgradeDiamondItem(new Item.Properties()));

    public static final DeferredItem<Item> INDEX_UPGRADE_NETHERITE = ITEMS.register("index_range_upgrade_netherite",
            () -> new IndexRangeUpgradeNetheriteItem(new Item.Properties()));

    public static final DeferredItem<Item> ERASER = ITEMS.register("eraser",
            () -> new EraserItem(new Item.Properties().durability(64)));

    public static void register (IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

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

    public static final DeferredItem<Item> INDEX_CARD = ITEMS.register("index_card",
            () -> new IndexCardItem(new Item.Properties()));

    public static final DeferredItem<Item> RANGE_UPGRADE_ONE = ITEMS.register("range_upgrade_tier_1",
            () -> new RangeUpgradeTierOne(new Item.Properties()));

    public static final DeferredItem<Item> RANGE_UPGRADE_TWO = ITEMS.register("range_upgrade_tier_2",
            () -> new RangeUpgradeTierTwo(new Item.Properties()));

    public static final DeferredItem<Item> RANGE_UPGRADE_THREE = ITEMS.register("range_upgrade_tier_3",
            () -> new RangeUpgradeTierThree(new Item.Properties()));

    public static void register (IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

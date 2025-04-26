package com.blocklogic.realfilingreborn.item;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.item.custom.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealFilingReborn.MODID);

    public static final DeferredItem<Item> FILING_FOLDER = ITEMS.register("filing_folder",
            () -> new FilingFolderItem(new Item.Properties()));

    public static final DeferredItem<Item> NBT_FILING_FOLDER = ITEMS.register("nbt_filing_folder",
            () -> new NBTFilingFolderItem(new Item.Properties()));

    public static final DeferredItem<Item> INDEX_CARD = ITEMS.register("index_card",
            () -> new IndexCardItem(new Item.Properties()));

    public static final DeferredItem<Item> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade",
            () -> new CapacityUpgradeItem(new Item.Properties()){
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                    tooltipComponents.add(Component.translatable("tooltip.realfilingreborn.capacity_info"));
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                }
            });

    public static final DeferredItem<Item> ERASER = ITEMS.register("eraser",
            () -> new EraserItem(new Item.Properties().durability(64)));

    public static void register (IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

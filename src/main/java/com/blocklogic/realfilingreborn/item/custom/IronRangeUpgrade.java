package com.blocklogic.realfilingreborn.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class IronRangeUpgrade extends Item {
    public IronRangeUpgrade(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.realfilingreborn.iron_range_upgrade.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

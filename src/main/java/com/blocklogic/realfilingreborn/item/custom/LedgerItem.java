package com.blocklogic.realfilingreborn.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class LedgerItem extends Item {
    public LedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger_info")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger_usage")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}

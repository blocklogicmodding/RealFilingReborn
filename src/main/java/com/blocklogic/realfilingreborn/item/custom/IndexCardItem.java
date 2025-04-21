package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class IndexCardItem extends Item {
    public IndexCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Check if the block is a Filing Index
        if (context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.FILING_INDEX.get())) {
            // Store the position
            context.getItemInHand().set(ModDataComponents.COORDINATES, context.getClickedPos());

            // Add feedback message to the player
            if (!context.getLevel().isClientSide()) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.realfilingreborn.index_card_linked")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }

            return InteractionResult.SUCCESS;
        } else {
            // If not a Filing Index, provide feedback
            if (!context.getLevel().isClientSide()) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.realfilingreborn.index_card_needs_index")
                                .withStyle(ChatFormatting.RED),
                        true);
            }

            return InteractionResult.FAIL;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.get(ModDataComponents.COORDINATES) != null) {
            tooltipComponents.add(Component.translatable("tooltip.realfilingreborn.index_card_linked",
                            Component.literal(stack.get(ModDataComponents.COORDINATES).toShortString())
                                    .withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.realfilingreborn.index_card_unlinked")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (!level.getBlockState(context.getClickedPos()).is(ModBlocks.FILING_INDEX.get())) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.index_card_needs_index")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResult.FAIL;
        }

        ItemStack heldStack = context.getItemInHand();

        // Create new linked card
        ItemStack linkedCard = new ItemStack(this);
        linkedCard.set(ModDataComponents.COORDINATES, context.getClickedPos());

        // Reduce original stack first (important!)
        heldStack.shrink(1);

        // Handle new card placement
        if (player != null) {
            if (!player.getInventory().add(linkedCard)) {
                player.drop(linkedCard, false);
            }

            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.index_card_linked")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }

        if (!level.isClientSide()) {
            level.playSound(null, context.getClickedPos(),
                    SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.8f, 1.2f);
        }

        return InteractionResult.SUCCESS;
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
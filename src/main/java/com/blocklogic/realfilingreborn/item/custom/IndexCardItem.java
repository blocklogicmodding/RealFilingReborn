package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
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

        // Skip if this is a filing cabinet - let the cabinet block handle it
        if (level.getBlockEntity(context.getClickedPos()) instanceof FilingCabinetBlockEntity) {
            return InteractionResult.PASS;
        }

        if (!level.getBlockState(context.getClickedPos()).is(ModBlocks.FILING_INDEX.get())) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.index_card_needs_index")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResult.FAIL;
        }

        // Check if the filing index is at capacity
        if (level.getBlockEntity(context.getClickedPos()) instanceof FilingIndexBlockEntity indexBE) {
            if (!indexBE.canAcceptMoreCabinets()) {
                if (!level.isClientSide() && player != null) {
                    // Check if it's at base capacity (64) or max capacity (128)
                    boolean hasUpgrade = !indexBE.inventory.getStackInSlot(0).isEmpty();
                    if (hasUpgrade) {
                        player.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_at_max_capacity")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.realfilingreborn.index_at_base_capacity")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                }
                return InteractionResult.FAIL;
            }
        }

        ItemStack heldStack = context.getItemInHand();

        ItemStack linkedCard = new ItemStack(this);
        linkedCard.set(ModDataComponents.COORDINATES, context.getClickedPos());

        heldStack.shrink(1);

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
    public boolean isFoil(ItemStack stack) {
        return stack.get(ModDataComponents.COORDINATES) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.get(ModDataComponents.COORDINATES) != null) {
            tooltipComponents.add(Component.translatable("tooltip.realfilingreborn.index_card_linked",
                            Component.literal(stack.get(ModDataComponents.COORDINATES).toShortString())
                                    .withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.realfilingreborn.index_card_unlinked")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
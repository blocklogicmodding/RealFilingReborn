package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CabinetConversionItem extends Item {
    public CabinetConversionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player == null) {
            return InteractionResult.PASS;
        }

        // Check if the block is a filing cabinet
        if (state.getBlock() instanceof FilingCabinetBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            // Get the block entity to check if it's empty
            if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity filingCabinetEntity) {
                // Check if the filing cabinet has any items in it
                boolean hasItems = false;
                for (int i = 0; i < filingCabinetEntity.inventory.getSlots(); i++) {
                    if (!filingCabinetEntity.inventory.getStackInSlot(i).isEmpty()) {
                        hasItems = true;
                        break;
                    }
                }

                if (hasItems) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.cabinet_must_be_empty")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }

                // Get the facing direction from the original cabinet
                BlockState newState = ModBlocks.FLUID_CABINET.get().defaultBlockState()
                        .setValue(FilingCabinetBlock.FACING, state.getValue(FilingCabinetBlock.FACING));

                // Replace the block
                level.setBlock(pos, newState, Block.UPDATE_ALL);

                // Play conversion sound
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.2f);

                // Consume the conversion kit
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }

                // Success message
                player.displayClientMessage(Component.translatable("message.realfilingreborn.cabinet_converted")
                        .withStyle(ChatFormatting.GREEN), true);

                return InteractionResult.SUCCESS;
            }
        }
        // Check if trying to use on fluid cabinet (show message)
        else if (state.is(ModBlocks.FLUID_CABINET.get())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.already_fluid_cabinet")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.FAIL;
        }
        // Wrong block type
        else {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.conversion_only_filing_cabinet")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realfilingreborn.cabinet_conversion_kit_info")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.realfilingreborn.cabinet_conversion_usage")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
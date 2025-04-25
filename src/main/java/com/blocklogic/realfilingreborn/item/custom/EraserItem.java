package com.blocklogic.realfilingreborn.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EraserItem extends Item {
    public EraserItem(Properties properties) {
        super(properties.durability(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack eraserStack = player.getItemInHand(hand);
        ItemStack folderStack = player.getItemInHand(InteractionHand.OFF_HAND);

        if (folderStack.isEmpty() ||
                (!(folderStack.getItem() instanceof FilingFolderItem) &&
                        !(folderStack.getItem() instanceof NBTFilingFolderItem))) {
            return InteractionResultHolder.pass(eraserStack);
        }

        boolean hasFolderContents = false;
        boolean isUnassigned = false;

        if (folderStack.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItems() != null && !contents.storedItems().isEmpty()) {
                hasFolderContents = true;
            }
            if (contents == null || contents.storedItemId().isEmpty()) {
                isUnassigned = true;
            }
        } else {
            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents != null && contents.count() > 0) {
                hasFolderContents = true;
            }
            if (contents == null || contents.storedItemId().isEmpty()) {
                isUnassigned = true;
            }
        }

        if (hasFolderContents) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_not_empty")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.consume(eraserStack); // Use consume to prevent further processing
        }

        if (isUnassigned) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_not_assigned")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.consume(eraserStack);
        }

        if (folderStack.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent()) {
                folderStack.remove(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(),
                        new NBTFilingFolderItem.NBTFolderContents(Optional.empty(), new ArrayList<>()));

                if (!player.getAbilities().instabuild) {
                    int currentDamage = eraserStack.getDamageValue();
                    int maxDamage = eraserStack.getMaxDamage();

                    if (currentDamage < maxDamage) {
                        eraserStack.setDamageValue(currentDamage + 1);
                    }
                }

                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_erased")
                            .withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.5f, 1.0f);
                }

                return InteractionResultHolder.success(eraserStack);
            }
        } else {
            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent()) {
                ItemStack freshFolder = new ItemStack(folderStack.getItem());
                freshFolder.setCount(folderStack.getCount());
                player.setItemInHand(InteractionHand.OFF_HAND, freshFolder);

                if (!player.getAbilities().instabuild) {
                    int currentDamage = eraserStack.getDamageValue();
                    int maxDamage = eraserStack.getMaxDamage();

                    if (currentDamage < maxDamage) {
                        eraserStack.setDamageValue(currentDamage + 1);
                    }
                }

                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_erased")
                            .withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.5f, 1.0f);
                }

                return InteractionResultHolder.success(eraserStack);
            }
        }

        return InteractionResultHolder.pass(eraserStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realfilingreborn.eraser_info")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
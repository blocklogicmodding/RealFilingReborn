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
import net.minecraft.world.level.Level;

import java.util.Optional;

public class EraserItem extends Item {
    public EraserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ItemStack eraserStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack folderStack = player.getItemInHand(InteractionHand.OFF_HAND);

        if (folderStack.isEmpty()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.eraser_no_folder")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(eraserStack);
        }

        if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
            FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent() && contents.count() <= 0) {
                folderStack.set(FilingFolderItem.FOLDER_CONTENTS.value(),
                        new FilingFolderItem.FolderContents(Optional.empty(), 0));

                if (!level.isClientSide()) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8f, 1.3f);
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }

                return InteractionResultHolder.success(eraserStack);
            }
            else if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_not_empty")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResultHolder.fail(eraserStack);
            }
            else {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_not_registered")
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }
                return InteractionResultHolder.fail(eraserStack);
            }
        }

        else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent() &&
                    (contents.storedItems() == null || contents.storedItems().isEmpty())) {
                folderStack.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(),
                        new NBTFilingFolderItem.NBTFolderContents(Optional.empty(), new java.util.ArrayList<>()));

                if (!level.isClientSide()) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8f, 1.3f);
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }

                return InteractionResultHolder.success(eraserStack);
            }
            else if (contents != null && contents.storedItemId().isPresent() &&
                    contents.storedItems() != null && !contents.storedItems().isEmpty()) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_not_empty")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResultHolder.fail(eraserStack);
            }
            else {
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.eraser_not_registered")
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }
                return InteractionResultHolder.fail(eraserStack);
            }
        }
        else {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.eraser_not_folder")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(eraserStack);
        }
    }
}
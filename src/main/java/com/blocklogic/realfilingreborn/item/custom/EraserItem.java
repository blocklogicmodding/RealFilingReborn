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
        ItemStack itemInOffHand = player.getItemInHand(InteractionHand.OFF_HAND);

        if (itemInOffHand.isEmpty() ||
                (!(itemInOffHand.getItem() instanceof FilingFolderItem) &&
                        !(itemInOffHand.getItem() instanceof NBTFilingFolderItem) &&
                        !(itemInOffHand.getItem() instanceof FluidCanisterItem))) {
            return InteractionResultHolder.pass(eraserStack);
        }

        boolean hasContents = false;
        boolean isUnassigned = false;


        if (itemInOffHand.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = itemInOffHand.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
            if (contents != null && contents.storedItems() != null && !contents.storedItems().isEmpty()) {
                hasContents = true;
            }
            if (contents == null || contents.storedItemId().isEmpty()) {
                isUnassigned = true;
            }
        }

        else if (itemInOffHand.getItem() instanceof FilingFolderItem) {
            FilingFolderItem.FolderContents contents = itemInOffHand.get(FilingFolderItem.FOLDER_CONTENTS.value());
            if (contents != null && contents.count() > 0) {
                hasContents = true;
            }
            if (contents == null || contents.storedItemId().isEmpty()) {
                isUnassigned = true;
            }
        }

        else if (itemInOffHand.getItem() instanceof FluidCanisterItem) {
            FluidCanisterItem.CanisterContents contents = itemInOffHand.get(FluidCanisterItem.CANISTER_CONTENTS.value());
            if (contents != null && contents.amount() > 0) {
                hasContents = true;
            }
            if (contents == null || contents.storedFluidId().isEmpty()) {
                isUnassigned = true;
            }
        }

        if (hasContents) {
            if (!level.isClientSide()) {
                if (itemInOffHand.getItem() instanceof FluidCanisterItem) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_not_empty")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_not_empty")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResultHolder.consume(eraserStack);
        }

        if (isUnassigned) {
            if (!level.isClientSide()) {
                if (itemInOffHand.getItem() instanceof FluidCanisterItem) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_not_assigned")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_not_assigned")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResultHolder.consume(eraserStack);
        }

        if (itemInOffHand.getItem() instanceof NBTFilingFolderItem) {
            NBTFilingFolderItem.NBTFolderContents contents = itemInOffHand.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent()) {
                itemInOffHand.remove(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                itemInOffHand.set(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value(),
                        new NBTFilingFolderItem.NBTFolderContents(Optional.empty(), new ArrayList<>()));

                if (!player.getAbilities().instabuild) {
                    damageEraser(eraserStack);
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

        else if (itemInOffHand.getItem() instanceof FilingFolderItem) {
            FilingFolderItem.FolderContents contents = itemInOffHand.get(FilingFolderItem.FOLDER_CONTENTS.value());

            if (contents != null && contents.storedItemId().isPresent()) {
                ItemStack freshFolder = new ItemStack(itemInOffHand.getItem());
                freshFolder.setCount(itemInOffHand.getCount());
                player.setItemInHand(InteractionHand.OFF_HAND, freshFolder);

                if (!player.getAbilities().instabuild) {
                    damageEraser(eraserStack);
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

        else if (itemInOffHand.getItem() instanceof FluidCanisterItem) {
            FluidCanisterItem.CanisterContents contents = itemInOffHand.get(FluidCanisterItem.CANISTER_CONTENTS.value());

            if (contents != null && contents.storedFluidId().isPresent()) {
                itemInOffHand.remove(FluidCanisterItem.CANISTER_CONTENTS.value());
                itemInOffHand.set(FluidCanisterItem.CANISTER_CONTENTS.value(),
                        new FluidCanisterItem.CanisterContents(Optional.empty(), 0));

                if (!player.getAbilities().instabuild) {
                    damageEraser(eraserStack);
                }

                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_erased")
                            .withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.5f, 1.0f);
                }

                return InteractionResultHolder.success(eraserStack);
            }
        }

        return InteractionResultHolder.pass(eraserStack);
    }

    private void damageEraser(ItemStack eraserStack) {
        int currentDamage = eraserStack.getDamageValue();
        int maxDamage = eraserStack.getMaxDamage();

        if (currentDamage < maxDamage) {
            eraserStack.setDamageValue(currentDamage + 1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realfilingreborn.eraser_info")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
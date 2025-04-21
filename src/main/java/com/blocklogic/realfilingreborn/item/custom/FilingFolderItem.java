package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public class FilingFolderItem extends Item {
    public record FolderContents(Optional<ResourceLocation> storedItemId, int count) {}

    private static final Codec<FolderContents> FOLDER_CONTENTS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("storedItemId").forGetter(FolderContents::storedItemId),
                    Codec.INT.fieldOf("count").forGetter(FolderContents::count)
            ).apply(instance, FolderContents::new)
    );

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8
                    .map(ResourceLocation::parse, ResourceLocation::toString);

    private static final StreamCodec<ByteBuf, FolderContents> FOLDER_CONTENTS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(RESOURCE_LOCATION_STREAM_CODEC), FolderContents::storedItemId,
            ByteBufCodecs.INT, FolderContents::count,
            FolderContents::new
    );

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FolderContents>> FOLDER_CONTENTS =
            DATA_COMPONENTS.register("folder_contents",
                    () -> DataComponentType.<FolderContents>builder()
                            .persistent(FOLDER_CONTENTS_CODEC)
                            .networkSynchronized(FOLDER_CONTENTS_STREAM_CODEC)
                            .build());

    public FilingFolderItem(Properties properties) {
        super(properties);
        properties.component(FOLDER_CONTENTS.value(), new FolderContents(Optional.empty(), 0));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack folderStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(folderStack);
        }

        ItemStack itemToStore = player.getItemInHand(InteractionHand.OFF_HAND);

        if (itemToStore.isEmpty() || itemToStore.getItem() instanceof FilingFolderItem) {
            if (itemToStore.getItem() instanceof FilingFolderItem) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.no_folder_ception"), true);
                return InteractionResultHolder.pass(folderStack);
            }

            FolderContents contents = folderStack.get(FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new FolderContents(Optional.empty(), 0);
                folderStack.set(FOLDER_CONTENTS.value(), contents);
            }

            if (player.isShiftKeyDown() && contents.storedItemId().isPresent()) {
                return extractItems(level, player, folderStack, contents);
            } else {
                return InteractionResultHolder.pass(folderStack);
            }
        }

        if (!player.isShiftKeyDown() && folderStack.getCount() > 1) {
            ItemStack singleFolder = folderStack.copy();
            singleFolder.setCount(1);

            FolderContents contents = singleFolder.get(FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new FolderContents(Optional.empty(), 0);
                singleFolder.set(FOLDER_CONTENTS.value(), contents);
            }

            InteractionResultHolder<ItemStack> result = storeItems(level, player, singleFolder, itemToStore, contents);
            ItemStack modifiedFolder = result.getObject();

            folderStack.shrink(1);

            if (!player.getInventory().add(modifiedFolder)) {
                player.drop(modifiedFolder, false);
            }

            return InteractionResultHolder.success(folderStack);
        } else {
            FolderContents contents = folderStack.get(FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new FolderContents(Optional.empty(), 0);
                folderStack.set(FOLDER_CONTENTS.value(), contents);
            }

            return storeItems(level, player, folderStack, itemToStore, contents);
        }
    }

    private InteractionResultHolder<ItemStack> extractItems(Level level, Player player, ItemStack folderStack, FolderContents contents) {
        if (contents == null || contents.storedItemId().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        if (contents.count() <= 0) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        ResourceLocation itemId = contents.storedItemId().get();
        Item item = BuiltInRegistries.ITEM.get(itemId);

        ItemStack dummyStack = new ItemStack(item);
        int extractAmount = Math.min(Math.min(contents.count(), item.getMaxStackSize(dummyStack)), 64);

        ItemStack extractedStack = new ItemStack(item, extractAmount);

        int newCount = contents.count() - extractAmount;
        FolderContents newContents = new FolderContents(
                contents.storedItemId(),
                Math.max(0, newCount)
        );

        folderStack.set(FOLDER_CONTENTS.value(), newContents);

        if (player.getInventory().add(extractedStack)) {
            return InteractionResultHolder.success(folderStack);
        } else {
            player.drop(extractedStack, false);
            return InteractionResultHolder.success(folderStack);
        }
    }

    private InteractionResultHolder<ItemStack> storeItems(Level level, Player player, ItemStack folderStack,  ItemStack itemToStore, FolderContents contents) {
        if (itemToStore.isEmpty() || itemToStore.getItem() instanceof FilingFolderItem) {
            return InteractionResultHolder.pass(folderStack);
        }

        ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(itemToStore.getItem());

        Optional<ResourceLocation> currentItemIdOpt = contents.storedItemId();
        ResourceLocation effectiveItemId;
        if (currentItemIdOpt.isEmpty()) {
            effectiveItemId = newItemId;
        } else {
            effectiveItemId = currentItemIdOpt.get();

            if (!effectiveItemId.equals(newItemId)) {
                Item storedItem = BuiltInRegistries.ITEM.get(effectiveItemId);
                player.displayClientMessage(Component.translatable(
                        "message.realfilingreborn.wrong_item_type",
                        storedItem.getDescription().copy().withStyle(ChatFormatting.YELLOW)
                ), true);
                return InteractionResultHolder.fail(folderStack);
            }
        }

        int maxToAdd = Integer.MAX_VALUE - contents.count();
        int toAdd = Math.min(itemToStore.getCount(), maxToAdd);

        if (toAdd <= 0) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_full"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        FolderContents newContents = new FolderContents(
                Optional.of(effectiveItemId),
                contents.count() + toAdd
        );

        folderStack.set(FOLDER_CONTENTS.value(), newContents);

        itemToStore.shrink(toAdd);

        return InteractionResultHolder.success(folderStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FolderContents contents = stack.get(FOLDER_CONTENTS.value());

        if (contents != null && contents.storedItemId().isPresent()) {
            ResourceLocation itemId = contents.storedItemId().get();
            Item item = BuiltInRegistries.ITEM.get(itemId);
            tooltip.add(Component.translatable("tooltip.realfilingreborn.stored_item",
                            Component.literal(item.getDescription().getString()).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));

            if (contents.count() > 0) {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.item_count",
                                Component.literal(String.format("%,d", contents.count())).withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.empty_folder")
                        .withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.unregistered_folder")
                    .withStyle(ChatFormatting.GRAY));
        }

        super.appendHoverText(stack, context, tooltip, flag);
    }


}
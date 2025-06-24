package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.screen.custom.FilingFolderMenu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NBTFilingFolderItem extends Item {

    public record SerializedItemStack(ItemStack stack) {}
    public static final int MAX_NBT_ITEMS = 128;

    public record NBTFolderContents(
            Optional<ResourceLocation> storedItemId,
            List<SerializedItemStack> storedItems
    ) {}

    private static final Codec<SerializedItemStack> SERIALIZED_STACK_CODEC =
            ItemStack.CODEC.xmap(SerializedItemStack::new, SerializedItemStack::stack);

    private static final StreamCodec<RegistryFriendlyByteBuf, SerializedItemStack> SERIALIZED_STACK_STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, SerializedItemStack::stack,
                    SerializedItemStack::new
            );

    private static final Codec<NBTFolderContents> NBT_FOLDER_CONTENTS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("storedItemId").forGetter(NBTFolderContents::storedItemId),
                    Codec.list(SERIALIZED_STACK_CODEC).fieldOf("storedItems").forGetter(NBTFolderContents::storedItems)
            ).apply(instance, NBTFolderContents::new)
    );

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8
                    .map(ResourceLocation::parse, ResourceLocation::toString);

    private static final StreamCodec<RegistryFriendlyByteBuf, NBTFolderContents> NBT_FOLDER_CONTENTS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(RESOURCE_LOCATION_STREAM_CODEC),
            NBTFolderContents::storedItemId,
            SERIALIZED_STACK_STREAM_CODEC.apply(ByteBufCodecs.list(256)),
            NBTFolderContents::storedItems,
            NBTFolderContents::new
    );

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NBTFolderContents>> NBT_FOLDER_CONTENTS =
            DATA_COMPONENTS.register("nbt_folder_contents",
                    () -> DataComponentType.<NBTFolderContents>builder()
                            .persistent(NBT_FOLDER_CONTENTS_CODEC)
                            .networkSynchronized(NBT_FOLDER_CONTENTS_STREAM_CODEC)
                            .build());

    public NBTFilingFolderItem(Properties properties) {
        super(properties.component(
                NBT_FOLDER_CONTENTS.value(), new NBTFolderContents(Optional.empty(), new ArrayList<>())
        ));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack folderStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(folderStack);
        }

        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                int foundSlotIndex = -1;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i) == folderStack) {
                        foundSlotIndex = i;
                        break;
                    }
                }

                final int finalSlotIndex = foundSlotIndex;
                if (finalSlotIndex != -1) {
                    Component title = Component.translatable("gui.realfilingreborn.nbt_folder.title");

                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, playerEntity) -> new FilingFolderMenu(containerId, inventory, finalSlotIndex),
                            title
                    ), buf -> buf.writeInt(finalSlotIndex));

                    return InteractionResultHolder.success(folderStack);
                }
            }
            return InteractionResultHolder.success(folderStack);
        }

        ItemStack itemToStore = player.getItemInHand(InteractionHand.OFF_HAND);

        if (itemToStore.isEmpty() || itemToStore.getItem() instanceof FilingFolderItem || itemToStore.getItem() instanceof NBTFilingFolderItem) {
            if (itemToStore.getItem() instanceof FilingFolderItem || itemToStore.getItem() instanceof NBTFilingFolderItem) {
                player.displayClientMessage(Component.translatable("message.realfilingreborn.no_folder_ception"), true);
                return InteractionResultHolder.pass(folderStack);
            }

            NBTFolderContents contents = folderStack.get(NBT_FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new NBTFolderContents(Optional.empty(), new ArrayList<>());
                folderStack.set(NBT_FOLDER_CONTENTS.value(), contents);
            }

            if (player.isShiftKeyDown() && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                if (folderStack.getCount() > 1) {
                    ItemStack singleFolder = folderStack.copy();
                    singleFolder.setCount(1);

                    InteractionResultHolder<ItemStack> result = extractItem(level, player, singleFolder, contents);
                    ItemStack modifiedFolder = result.getObject();

                    if (result.getResult().consumesAction()) {
                        folderStack.shrink(1);

                        if (!player.getInventory().add(modifiedFolder)) {
                            player.drop(modifiedFolder, false);
                        }

                        return InteractionResultHolder.success(folderStack);
                    }
                    return result;
                }
                return extractItem(level, player, folderStack, contents);
            } else {
                return InteractionResultHolder.pass(folderStack);
            }
        }

        if (!hasSignificantNBT(itemToStore)) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.nbt_folder_requires_nbt"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        if (!player.isShiftKeyDown() && folderStack.getCount() > 1) {
            ItemStack singleFolder = folderStack.copy();
            singleFolder.setCount(1);

            NBTFolderContents contents = singleFolder.get(NBT_FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new NBTFolderContents(Optional.empty(), new ArrayList<>());
                singleFolder.set(NBT_FOLDER_CONTENTS.value(), contents);
            }

            InteractionResultHolder<ItemStack> result = storeItem(level, player, singleFolder, itemToStore, contents);
            ItemStack modifiedFolder = result.getObject();

            folderStack.shrink(1);

            if (!player.getInventory().add(modifiedFolder)) {
                player.drop(modifiedFolder, false);
            }

            return InteractionResultHolder.success(folderStack);
        } else {
            NBTFolderContents contents = folderStack.get(NBT_FOLDER_CONTENTS.value());
            if (contents == null) {
                contents = new NBTFolderContents(Optional.empty(), new ArrayList<>());
                folderStack.set(NBT_FOLDER_CONTENTS.value(), contents);
            }

            return storeItem(level, player, folderStack, itemToStore, contents);
        }
    }

    public static boolean hasSignificantNBT(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.isDamaged()) return true;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) return true;

        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) return true;

        if (stack.get(DataComponents.CUSTOM_NAME) != null) return true;

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) return true;

        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && (!potion.customEffects().isEmpty() || !potion.potion().isEmpty())) return true;

        return false;
    }

    private InteractionResultHolder<ItemStack> extractItem(Level level, Player player, ItemStack folderStack, NBTFolderContents contents) {
        if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        List<SerializedItemStack> items = new ArrayList<>(contents.storedItems());
        SerializedItemStack serializedItem = items.remove(items.size() - 1);

        ItemStack extracted = serializedItem.stack().copy();

        NBTFolderContents newContents = new NBTFolderContents(
                contents.storedItemId(),
                items
        );

        folderStack.set(NBT_FOLDER_CONTENTS.value(), newContents);

        if (player.getInventory().add(extracted)) {
            return InteractionResultHolder.success(folderStack);
        } else {
            player.drop(extracted, false);
            return InteractionResultHolder.success(folderStack);
        }
    }

    private InteractionResultHolder<ItemStack> storeItem(Level level, Player player, ItemStack folderStack, ItemStack itemToStore, NBTFolderContents contents) {
        if (itemToStore.isEmpty() || !hasSignificantNBT(itemToStore)) {
            return InteractionResultHolder.pass(folderStack);
        }

        ResourceLocation newItemId = BuiltInRegistries.ITEM.getKey(itemToStore.getItem());

        Optional<ResourceLocation> currentItemIdOpt = contents.storedItemId();
        if (currentItemIdOpt.isPresent() && !currentItemIdOpt.get().equals(newItemId)) {
            Item storedItem = BuiltInRegistries.ITEM.get(currentItemIdOpt.get());
            player.displayClientMessage(Component.translatable(
                    "message.realfilingreborn.wrong_item_type",
                    storedItem.getDescription().copy().withStyle(ChatFormatting.YELLOW)
            ), true);
            return InteractionResultHolder.fail(folderStack);
        }

        List<SerializedItemStack> currentItems = contents.storedItems() != null ? contents.storedItems() : new ArrayList<>();
        if (currentItems.size() >= MAX_NBT_ITEMS) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.nbt_folder_full"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        List<SerializedItemStack> newItems = new ArrayList<>(currentItems);

        ItemStack itemCopy = itemToStore.copy();
        itemCopy.setCount(1);
        newItems.add(new SerializedItemStack(itemCopy));

        NBTFolderContents newContents = new NBTFolderContents(
                Optional.of(newItemId),
                newItems
        );

        folderStack.set(NBT_FOLDER_CONTENTS.value(), newContents);

        itemToStore.shrink(1);

        return InteractionResultHolder.success(folderStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        NBTFolderContents contents = stack.get(NBT_FOLDER_CONTENTS.value());

        if (contents != null && contents.storedItemId().isPresent()) {
            ResourceLocation itemId = contents.storedItemId().get();
            Item item = BuiltInRegistries.ITEM.get(itemId);
            tooltip.add(Component.translatable("tooltip.realfilingreborn.stored_item",
                            Component.literal(item.getDescription().getString()).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));

            if (contents.storedItems() != null && !contents.storedItems().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.nbt_item_count",
                                Component.literal(String.valueOf(contents.storedItems().size())).withStyle(ChatFormatting.GREEN),
                                Component.literal(String.valueOf(MAX_NBT_ITEMS)).withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.GRAY));

                if (flag.isAdvanced() && !contents.storedItems().isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.realfilingreborn.nbt_items_stored")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    int maxToShow = Math.min(3, contents.storedItems().size());
                    for (int i = 0; i < maxToShow; i++) {
                        ItemStack storedItem = contents.storedItems().get(i).stack();
                        Component itemName = storedItem.getDisplayName();
                        tooltip.add(Component.literal(" - ").append(itemName)
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }

                    if (contents.storedItems().size() > maxToShow) {
                        tooltip.add(Component.translatable("tooltip.realfilingreborn.and_x_more",
                                        contents.storedItems().size() - maxToShow)
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    }
                }
            } else {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.empty_folder")
                        .withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.nbt_unregistered_folder")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.realfilingreborn.nbt_standard_folder_info")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));

        tooltip.add(Component.translatable("tooltip.realfilingreborn.folder_gui_hint")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));

        super.appendHoverText(stack, context, tooltip, flag);
    }
}
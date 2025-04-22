package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NBTFilingFolderItem extends Item {
    // Define a record to store serialized ItemStacks
    public record SerializedItemStack(ItemStack stack) {}

    // Define the folder contents record
    public record NBTFolderContents(
            Optional<ResourceLocation> storedItemId,
            List<SerializedItemStack> storedItems
    ) {}

    // Create codec for SerializedItemStack
    private static final Codec<SerializedItemStack> SERIALIZED_STACK_CODEC =
            ItemStack.CODEC.xmap(SerializedItemStack::new, SerializedItemStack::stack);

    // Stream codec for SerializedItemStack
    private static final StreamCodec<RegistryFriendlyByteBuf, SerializedItemStack> SERIALIZED_STACK_STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, SerializedItemStack::stack,
                    SerializedItemStack::new
            );

    // Create a codec for persistent storage of folder contents
    private static final Codec<NBTFolderContents> NBT_FOLDER_CONTENTS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("storedItemId").forGetter(NBTFolderContents::storedItemId),
                    Codec.list(SERIALIZED_STACK_CODEC).fieldOf("storedItems").forGetter(NBTFolderContents::storedItems)
            ).apply(instance, NBTFolderContents::new)
    );

    // Resource location stream codec (same as in FilingFolderItem)
    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8
                    .map(ResourceLocation::parse, ResourceLocation::toString);

    // Stream codec for networking the folder contents
    private static final StreamCodec<RegistryFriendlyByteBuf, NBTFolderContents> NBT_FOLDER_CONTENTS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(RESOURCE_LOCATION_STREAM_CODEC),
            NBTFolderContents::storedItemId,
            SERIALIZED_STACK_STREAM_CODEC.apply(ByteBufCodecs.list(256)),
            NBTFolderContents::storedItems,
            NBTFolderContents::new
    );

    // Register the data component
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

        ItemStack itemToStore = player.getItemInHand(InteractionHand.OFF_HAND);

        // Don't allow storing folders in folders
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

            // Extract item if shift-clicking with empty hand
            if (player.isShiftKeyDown() && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                return extractItem(level, player, folderStack, contents);
            } else {
                return InteractionResultHolder.pass(folderStack);
            }
        }

        // Only accept items with significant data components
        if (!hasSignificantNBT(itemToStore)) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.nbt_folder_requires_nbt"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        // Handle item insertion
        if (!player.isShiftKeyDown() && folderStack.getCount() > 1) {
            // Create a new folder for a stack of folders
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

    private boolean hasSignificantNBT(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check for damage
        if (stack.isDamaged()) return true;

        // Check for enchantments
        if (stack.get(DataComponents.ENCHANTMENTS) != null &&
                !stack.get(DataComponents.ENCHANTMENTS).isEmpty()) return true;

        // Check for custom name
        if (stack.get(DataComponents.CUSTOM_NAME) != null) return true;

        // Check for lore
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            return true;
        }

        // Other checks for significant data components could be added here

        return false;
    }

    private InteractionResultHolder<ItemStack> extractItem(Level level, Player player, ItemStack folderStack, NBTFolderContents contents) {
        if (contents == null || contents.storedItemId().isEmpty() || contents.storedItems().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.folder_empty"), true);
            return InteractionResultHolder.fail(folderStack);
        }

        // Extract the last item (for LIFO behavior)
        List<SerializedItemStack> items = new ArrayList<>(contents.storedItems());
        SerializedItemStack serializedItem = items.remove(items.size() - 1);

        // Get the stored item with all its data
        ItemStack extracted = serializedItem.stack().copy();

        // Update the folder contents
        NBTFolderContents newContents = new NBTFolderContents(
                contents.storedItemId(),
                items
        );

        folderStack.set(NBT_FOLDER_CONTENTS.value(), newContents);

        // Give the item to the player
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

        // Check if folder is already registered for a different item type
        Optional<ResourceLocation> currentItemIdOpt = contents.storedItemId();
        if (currentItemIdOpt.isPresent() && !currentItemIdOpt.get().equals(newItemId)) {
            Item storedItem = BuiltInRegistries.ITEM.get(currentItemIdOpt.get());
            player.displayClientMessage(Component.translatable(
                    "message.realfilingreborn.wrong_item_type",
                    storedItem.getDescription().copy().withStyle(ChatFormatting.YELLOW)
            ), true);
            return InteractionResultHolder.fail(folderStack);
        }

        // Create a new list with the existing items
        List<SerializedItemStack> newItems = new ArrayList<>(contents.storedItems() != null ? contents.storedItems() : new ArrayList<>());

        // Store a copy of the item with all its data
        ItemStack itemCopy = itemToStore.copy();
        itemCopy.setCount(1); // Store one at a time
        newItems.add(new SerializedItemStack(itemCopy));

        // Update the folder contents
        NBTFolderContents newContents = new NBTFolderContents(
                Optional.of(newItemId),
                newItems
        );

        folderStack.set(NBT_FOLDER_CONTENTS.value(), newContents);

        // Remove the stored item from player's hand
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
                                Component.literal(String.valueOf(contents.storedItems().size())).withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.GRAY));

                // Show additional info for advanced tooltips
                if (flag.isAdvanced() && !contents.storedItems().isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.realfilingreborn.nbt_items_stored")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    // Show some information about the first few items
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

        super.appendHoverText(stack, context, tooltip, flag);
    }
}
package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.config.Config;
import com.blocklogic.realfilingreborn.screen.custom.FluidCanisterMenu;
import com.blocklogic.realfilingreborn.util.FluidHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public class FluidCanisterItem extends Item {
    public record CanisterContents(Optional<ResourceLocation> storedFluidId, int amount) {}

    private static final Codec<CanisterContents> CANISTER_CONTENTS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("storedFluidId").forGetter(CanisterContents::storedFluidId),
                    Codec.INT.fieldOf("amount").forGetter(CanisterContents::amount)
            ).apply(instance, CanisterContents::new)
    );

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8
                    .map(ResourceLocation::parse, ResourceLocation::toString);

    private static final StreamCodec<ByteBuf, CanisterContents> CANISTER_CONTENTS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(RESOURCE_LOCATION_STREAM_CODEC), CanisterContents::storedFluidId,
            ByteBufCodecs.INT, CanisterContents::amount,
            CanisterContents::new
    );

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CanisterContents>> CANISTER_CONTENTS =
            DATA_COMPONENTS.register("canister_contents",
                    () -> DataComponentType.<CanisterContents>builder()
                            .persistent(CANISTER_CONTENTS_CODEC)
                            .networkSynchronized(CANISTER_CONTENTS_STREAM_CODEC)
                            .build());

    public FluidCanisterItem(Properties properties) {
        super(properties);
        properties.component(CANISTER_CONTENTS.value(), new CanisterContents(Optional.empty(), 0));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack canisterStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(canisterStack);
        }

        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                int foundSlotIndex = -1;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i) == canisterStack) {
                        foundSlotIndex = i;
                        break;
                    }
                }

                final int finalSlotIndex = foundSlotIndex;
                if (finalSlotIndex != -1) {
                    Component title = Component.translatable("gui.realfilingreborn.canister.title");

                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, playerEntity) -> new FluidCanisterMenu(containerId, inventory, finalSlotIndex),
                            title
                    ), buf -> buf.writeInt(finalSlotIndex));

                    return InteractionResultHolder.success(canisterStack);
                }
            }
            return InteractionResultHolder.success(canisterStack);
        }

        ItemStack bucketStack = player.getItemInHand(InteractionHand.OFF_HAND);

        if (bucketStack.isEmpty() || !(bucketStack.getItem() instanceof BucketItem)) {
            return InteractionResultHolder.pass(canisterStack);
        }

        BucketItem bucketItem = (BucketItem) bucketStack.getItem();
        Fluid fluid = bucketItem.content;

        if (!FluidHelper.isValidFluid(fluid)) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.invalid_fluid"), true);
            return InteractionResultHolder.pass(canisterStack);
        }

        if (!player.isShiftKeyDown() && canisterStack.getCount() > 1) {
            ItemStack singleCanister = canisterStack.copy();
            singleCanister.setCount(1);

            CanisterContents contents = singleCanister.get(CANISTER_CONTENTS.value());
            if (contents == null) {
                contents = new CanisterContents(Optional.empty(), 0);
                singleCanister.set(CANISTER_CONTENTS.value(), contents);
            }

            InteractionResultHolder<ItemStack> result = storeFluid(level, player, singleCanister, bucketStack, contents);
            ItemStack modifiedCanister = result.getObject();

            canisterStack.shrink(1);

            if (!player.getInventory().add(modifiedCanister)) {
                player.drop(modifiedCanister, false);
            }

            return InteractionResultHolder.success(canisterStack);
        } else {
            CanisterContents contents = canisterStack.get(CANISTER_CONTENTS.value());
            if (contents == null) {
                contents = new CanisterContents(Optional.empty(), 0);
                canisterStack.set(CANISTER_CONTENTS.value(), contents);
            }

            return storeFluid(level, player, canisterStack, bucketStack, contents);
        }
    }

    private InteractionResultHolder<ItemStack> extractFluid(Level level, Player player, ItemStack canisterStack, CanisterContents contents) {
        if (contents == null || contents.storedFluidId().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_empty"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        if (contents.amount() <= 0) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_empty"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        int emptyBucketCount = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.BUCKET) {
                emptyBucketCount += stack.getCount();
            }
        }

        if (emptyBucketCount <= 0) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.need_empty_bucket"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        ResourceLocation fluidId = contents.storedFluidId().get();

        ItemStack bucketToGive = FluidHelper.getBucketForFluid(fluidId);
        if (bucketToGive.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.no_bucket_for_fluid"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        int extractAmount = Math.min(contents.amount(), 1000);

        if (extractAmount < 1000) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.not_enough_fluid"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        boolean bucketRemoved = false;
        for (int i = 0; i < player.getInventory().getContainerSize() && !bucketRemoved; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.BUCKET) {
                stack.shrink(1);
                bucketRemoved = true;
            }
        }

        if (!bucketRemoved) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.need_empty_bucket"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        int newAmount = contents.amount() - 1000;
        CanisterContents newContents = new CanisterContents(
                contents.storedFluidId(),
                Math.max(0, newAmount)
        );

        canisterStack.set(CANISTER_CONTENTS.value(), newContents);

        if (player.getInventory().add(bucketToGive)) {
            return InteractionResultHolder.success(canisterStack);
        } else {
            player.drop(bucketToGive, false);
            return InteractionResultHolder.success(canisterStack);
        }
    }

    private InteractionResultHolder<ItemStack> storeFluid(Level level, Player player, ItemStack canisterStack, ItemStack bucketStack, CanisterContents contents) {
        if (!(bucketStack.getItem() instanceof BucketItem bucketItem)) {
            return InteractionResultHolder.pass(canisterStack);
        }

        Fluid fluid = bucketItem.content;
        if (!FluidHelper.isValidFluid(fluid)) {
            return InteractionResultHolder.pass(canisterStack);
        }

        if (contents == null) {
            contents = new CanisterContents(Optional.empty(), 0);
            canisterStack.set(CANISTER_CONTENTS.value(), contents);
        }

        ResourceLocation newFluidId = FluidHelper.getStillFluid(FluidHelper.getFluidId(fluid));

        if (contents == null) {
            return InteractionResultHolder.fail(canisterStack);
        }

        Optional<ResourceLocation> currentFluidIdOpt = contents.storedFluidId();
        ResourceLocation effectiveFluidId;
        if (currentFluidIdOpt.isEmpty()) {
            effectiveFluidId = newFluidId;
        } else {
            effectiveFluidId = currentFluidIdOpt.get();

            if (!FluidHelper.areFluidsCompatible(effectiveFluidId, newFluidId)) {
                player.displayClientMessage(Component.translatable(
                        "message.realfilingreborn.wrong_fluid_type",
                        Component.literal(FluidHelper.getFluidDisplayName(effectiveFluidId)).withStyle(ChatFormatting.YELLOW)
                ), true);
                return InteractionResultHolder.fail(canisterStack);
            }
        }

        int maxToAdd = Config.getMaxCanisterStorage() - contents.amount();
        int toAdd = Math.min(1000, maxToAdd);

        if (toAdd <= 0) {
            player.displayClientMessage(Component.translatable("message.realfilingreborn.canister_full"), true);
            return InteractionResultHolder.fail(canisterStack);
        }

        CanisterContents newContents = new CanisterContents(
                Optional.of(effectiveFluidId),
                contents.amount() + toAdd
        );

        canisterStack.set(CANISTER_CONTENTS.value(), newContents);

        bucketStack.shrink(1);
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        if (!player.getInventory().add(emptyBucket)) {
            player.drop(emptyBucket, false);
        }

        return InteractionResultHolder.success(canisterStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CanisterContents contents = stack.get(CANISTER_CONTENTS.value());

        if (contents != null && contents.storedFluidId().isPresent()) {
            ResourceLocation fluidId = contents.storedFluidId().get();
            String fluidName = FluidHelper.getFluidDisplayName(fluidId);
            tooltip.add(Component.translatable("tooltip.realfilingreborn.stored_fluid",
                            Component.literal(fluidName).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));

            if (contents.amount() > 0) {
                int buckets = contents.amount() / 1000;
                int millibuckets = contents.amount() % 1000;
                String amountText = buckets > 0 ?
                        (millibuckets > 0 ? buckets + "." + (millibuckets / 100) + "B" : buckets + "B") :
                        millibuckets + "mB";

                tooltip.add(Component.translatable("tooltip.realfilingreborn.fluid_amount",
                                Component.literal(amountText).withStyle(ChatFormatting.BLUE))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.empty_canister")
                        .withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.unregistered_canister")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.realfilingreborn.canister_info")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));

        tooltip.add(Component.translatable("tooltip.realfilingreborn.canister_gui_hint")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));

        super.appendHoverText(stack, context, tooltip, flag);
    }
}
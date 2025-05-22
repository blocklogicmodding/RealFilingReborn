package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LedgerItem extends Item {

    public enum LedgerMode {
        Add("add"),
        Remove("remove");

        private final String name;

        LedgerMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public LedgerMode toggle() {
            return this == Add ? Remove : Add;
        }
    }

    public record LedgerData(
            List<BlockPos> knownIndices,
            Optional<BlockPos> selectedIndex,
            LedgerMode mode
    ) {}

    private static final Codec<LedgerData> LEDGER_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(BlockPos.CODEC).fieldOf("knownIndices").forGetter(LedgerData::knownIndices),
                    BlockPos.CODEC.optionalFieldOf("selectedIndex").forGetter(LedgerData::selectedIndex),
                    Codec.STRING.xmap(
                            name -> name.equals("remove") ? LedgerMode.Remove : LedgerMode.Add,
                            LedgerMode::getName
                    ).fieldOf("mode").forGetter(LedgerData::mode)
            ).apply(instance, LedgerData::new)
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, BlockPos> BLOCKPOS_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, BlockPos::getX,
                    ByteBufCodecs.INT, BlockPos::getY,
                    ByteBufCodecs.INT, BlockPos::getZ,
                    BlockPos::new
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, LedgerData> LEDGER_DATA_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BLOCKPOS_STREAM_CODEC),
            LedgerData::knownIndices,
            ByteBufCodecs.optional(BLOCKPOS_STREAM_CODEC),
            LedgerData::selectedIndex,
            ByteBufCodecs.STRING_UTF8.map(
                    name -> name.equals("remove") ? LedgerMode.Remove : LedgerMode.Add,
                    LedgerMode::getName
            ),
            LedgerData::mode,
            LedgerData::new
    );

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LedgerData>> LEDGER_DATA =
            DATA_COMPONENTS.register("ledger_data",
                    () -> DataComponentType.<LedgerData>builder()
                            .persistent(LEDGER_DATA_CODEC)
                            .networkSynchronized(LEDGER_DATA_STREAM_CODEC)
                            .build());

    public LedgerItem(Properties properties) {
        super(properties.component(
                LEDGER_DATA.value(),
                new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add)
        ));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack ledger = context.getItemInHand();

        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        LedgerData data = ledger.get(LEDGER_DATA.value());
        if (data == null) {
            data = new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add);
        }

        if (blockEntity instanceof FilingIndexBlockEntity && level.getBlockState(pos).getBlock() instanceof FilingIndexBlock) {
            return handleIndexInteraction(ledger, data, pos, player);
        }

        if (blockEntity instanceof FilingCabinetBlockEntity && level.getBlockState(pos).getBlock() instanceof FilingCabinetBlock) {
            return handleCabinetInteraction(ledger, data, pos, player, level);
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleIndexInteraction(ItemStack ledger, LedgerData data, BlockPos indexPos, Player player) {
        data = cleanupBrokenIndices(data, player.level());

        List<BlockPos> knownIndices = new ArrayList<>(data.knownIndices());

        if (!knownIndices.contains(indexPos)) {
            knownIndices.add(indexPos);
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.index_registered",
                                    indexPos.getX(), indexPos.getY(), indexPos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        }

        LedgerData newData = new LedgerData(knownIndices, Optional.of(indexPos), data.mode());
        ledger.set(LEDGER_DATA.value(), newData);

        player.displayClientMessage(
                Component.translatable("message.realfilingreborn.ledger.index_selected",
                                indexPos.getX(), indexPos.getY(), indexPos.getZ())
                        .withStyle(ChatFormatting.YELLOW),
                true
        );

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);

        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleCabinetInteraction(ItemStack ledger, LedgerData data, BlockPos cabinetPos, Player player, Level level) {

        if (data.selectedIndex().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.no_index_selected")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        BlockPos indexPos = data.selectedIndex().get();
        BlockEntity indexEntity = level.getBlockEntity(indexPos);

        if (!(indexEntity instanceof FilingIndexBlockEntity filingIndexEntity)) {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.index_not_found")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        double distance = Math.sqrt(indexPos.distSqr(cabinetPos));
        int maxRange = getIndexRange(filingIndexEntity);

        if (distance > maxRange) {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.out_of_range",
                                    (int)distance, maxRange)
                            .withStyle(ChatFormatting.RED),
                    true
            );
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_SNARE, SoundSource.PLAYERS, 1.0f, 1.0f);
            return InteractionResult.FAIL;
        }

        if (data.mode() == LedgerMode.Add) {
            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                if (cabinet.isInNetwork()) {
                    player.displayClientMessage(
                            Component.translatable("message.realfilingreborn.cabinet_already_in_network")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    return InteractionResult.FAIL;
                }
            }

            if (filingIndexEntity.addConnectedCabinet(cabinetPos)) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.cabinet_added")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.cabinet_already_connected")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        } else {
            if (filingIndexEntity.removeConnectedCabinet(cabinetPos)) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.cabinet_removed")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.cabinet_not_connected")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private int getIndexRange(FilingIndexBlockEntity indexEntity) {
        ItemStack upgrade = indexEntity.inventory.getStackInSlot(0);

        if (upgrade.getItem() instanceof IndexRangeUpgradeNetheriteItem) {
            return 128;
        } else if (upgrade.getItem() instanceof IndexRangeUpgradeDiamondItem) {
            return 64;
        } else if (upgrade.getItem() instanceof IndexRangeUpgradeIronItem) {
            return 32;
        }

        return 16;
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        ItemStack ledger = player.getItemInHand(hand);
        LedgerData data = ledger.get(LEDGER_DATA.value());
        if (data == null) {
            data = new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add);
        }

        data = cleanupBrokenIndices(data, level);

        LedgerMode newMode = data.mode().toggle();
        LedgerData newData = new LedgerData(data.knownIndices(), data.selectedIndex(), newMode);
        ledger.set(LEDGER_DATA.value(), newData);

        if (newMode == LedgerMode.Add) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0f, 0.5f);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0F, 1.0f);
        }

        player.displayClientMessage(
                Component.translatable("message.realfilingreborn.ledger.mode_changed",
                                newMode.getName().toUpperCase())
                        .withStyle(newMode == LedgerMode.Add ? ChatFormatting.GREEN : ChatFormatting.RED),
                true
        );

        return InteractionResultHolder.success(ledger);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LedgerData data = stack.get(LEDGER_DATA.value());

        if (data != null) {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.mode",
                            data.mode().getName().toUpperCase())
                    .withStyle(data.mode() == LedgerMode.Add ? ChatFormatting.GREEN : ChatFormatting.RED));

            if (data.selectedIndex().isPresent()) {
                BlockPos pos = data.selectedIndex().get();
                tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.selected_index",
                                pos.getX(), pos.getY(), pos.getZ())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.no_index_selected")
                        .withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.known_indices",
                            data.knownIndices().size())
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.usage")
                .withStyle(ChatFormatting.DARK_GRAY));

        super.appendHoverText(stack, context, tooltip, flag);
    }

    private LedgerData cleanupBrokenIndices(LedgerData data, Level level) {
        List<BlockPos> validIndices = new ArrayList<>();
        Optional<BlockPos> validSelectedIndex = data.selectedIndex();

        for (BlockPos indexPos : data.knownIndices()) {
            if (level.isLoaded(indexPos) &&
                    level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity) {
                validIndices.add(indexPos);
            }
        }

        if (validSelectedIndex.isPresent()) {
            BlockPos selectedPos = validSelectedIndex.get();
            if (!level.isLoaded(selectedPos) ||
                    !(level.getBlockEntity(selectedPos) instanceof FilingIndexBlockEntity)) {
                validSelectedIndex = Optional.empty();
            }
        }

        if (validIndices.size() != data.knownIndices().size() ||
                !validSelectedIndex.equals(data.selectedIndex())) {
            return new LedgerData(validIndices, validSelectedIndex, data.mode());
        }

        return data;
    }
}
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

    public enum SelectionMode {
        Single("single"),
        Multiple("multiple");

        private final String name;

        SelectionMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public SelectionMode toggle() {
            return this == Single ? Multiple : Single;
        }
    }

    public record LedgerData(
            List<BlockPos> knownIndices,
            Optional<BlockPos> selectedIndex,
            LedgerMode mode,
            SelectionMode selectionMode,
            Optional<BlockPos> firstSelectionPos,
            Optional<BlockPos> secondSelectionPos
    ) {}

    private static final Codec<LedgerData> LEDGER_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(BlockPos.CODEC).fieldOf("knownIndices").forGetter(LedgerData::knownIndices),
                    BlockPos.CODEC.optionalFieldOf("selectedIndex").forGetter(LedgerData::selectedIndex),
                    Codec.STRING.xmap(
                            name -> name.equals("remove") ? LedgerMode.Remove : LedgerMode.Add,
                            LedgerMode::getName
                    ).fieldOf("mode").forGetter(LedgerData::mode),
                    Codec.STRING.xmap(
                            name -> name.equals("multiple") ? SelectionMode.Multiple : SelectionMode.Single,
                            SelectionMode::getName
                    ).optionalFieldOf("selectionMode", SelectionMode.Single).forGetter(LedgerData::selectionMode),
                    BlockPos.CODEC.optionalFieldOf("firstSelectionPos").forGetter(LedgerData::firstSelectionPos),
                    BlockPos.CODEC.optionalFieldOf("secondSelectionPos").forGetter(LedgerData::secondSelectionPos)
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
            ByteBufCodecs.STRING_UTF8.map(
                    name -> name.equals("multiple") ? SelectionMode.Multiple : SelectionMode.Single,
                    SelectionMode::getName
            ),
            LedgerData::selectionMode,
            ByteBufCodecs.optional(BLOCKPOS_STREAM_CODEC),
            LedgerData::firstSelectionPos,
            ByteBufCodecs.optional(BLOCKPOS_STREAM_CODEC),
            LedgerData::secondSelectionPos,
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
                new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add, SelectionMode.Single, Optional.empty(), Optional.empty())
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
            data = new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add, SelectionMode.Single, Optional.empty(), Optional.empty());
        }

        if (blockEntity instanceof FilingIndexBlockEntity && level.getBlockState(pos).getBlock() instanceof FilingIndexBlock) {
            return handleIndexInteraction(ledger, data, pos, player);
        }

        if (blockEntity instanceof FilingCabinetBlockEntity && level.getBlockState(pos).getBlock() instanceof FilingCabinetBlock) {
            if (data.selectionMode() == SelectionMode.Single) {
                return handleSingleCabinetInteraction(ledger, data, pos, player, level);
            } else {
                return handleMultipleCabinetInteraction(ledger, data, pos, player, level);
            }
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

        LedgerData newData = new LedgerData(knownIndices, Optional.of(indexPos), data.mode(), data.selectionMode(), Optional.empty(), Optional.empty());
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

    private InteractionResult handleSingleCabinetInteraction(ItemStack ledger, LedgerData data, BlockPos cabinetPos, Player player, Level level) {
        if (data.selectedIndex().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.no_index_selected")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        return performCabinetOperation(data, cabinetPos, player, level);
    }

    private InteractionResult handleMultipleCabinetInteraction(ItemStack ledger, LedgerData data, BlockPos cabinetPos, Player player, Level level) {
        if (data.selectedIndex().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.no_index_selected")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        if (data.firstSelectionPos().isEmpty()) {
            LedgerData newData = new LedgerData(
                    data.knownIndices(),
                    data.selectedIndex(),
                    data.mode(),
                    data.selectionMode(),
                    Optional.of(cabinetPos),
                    Optional.empty()
            );
            ledger.set(LEDGER_DATA.value(), newData);

            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.first_corner_selected",
                                    cabinetPos.getX(), cabinetPos.getY(), cabinetPos.getZ())
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);

            return InteractionResult.SUCCESS;
        } else {
            BlockPos firstCorner = data.firstSelectionPos().get();
            BlockPos secondCorner = cabinetPos;

            LedgerData tempData = new LedgerData(
                    data.knownIndices(),
                    data.selectedIndex(),
                    data.mode(),
                    data.selectionMode(),
                    data.firstSelectionPos(),
                    Optional.of(secondCorner)
            );
            ledger.set(LEDGER_DATA.value(), tempData);

            List<BlockPos> cabinetsInArea = getCabinetsInArea(level, firstCorner, secondCorner);

            if (cabinetsInArea.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.no_cabinets_in_area")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.FAIL;
            }

            int successCount = performBulkCabinetOperation(data, cabinetsInArea, player, level);

            LedgerData newData = new LedgerData(
                    data.knownIndices(),
                    data.selectedIndex(),
                    data.mode(),
                    data.selectionMode(),
                    Optional.empty(),
                    Optional.empty()
            );
            ledger.set(LEDGER_DATA.value(), newData);

            if (successCount > 0) {
                player.displayClientMessage(
                        Component.translatable(
                                        data.mode() == LedgerMode.Add ?
                                                "message.realfilingreborn.ledger.bulk_cabinets_added" :
                                                "message.realfilingreborn.ledger.bulk_cabinets_removed",
                                        successCount, cabinetsInArea.size())
                                .withStyle(ChatFormatting.GREEN),
                        true
                );

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.realfilingreborn.ledger.no_operations_performed")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
            }

            return InteractionResult.SUCCESS;
        }
    }

    private List<BlockPos> getCabinetsInArea(Level level, BlockPos corner1, BlockPos corner2) {
        List<BlockPos> cabinets = new ArrayList<>();

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof FilingCabinetBlock &&
                            level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity) {
                        cabinets.add(pos);
                    }
                }
            }
        }

        return cabinets;
    }

    private int performBulkCabinetOperation(LedgerData data, List<BlockPos> cabinetPositions, Player player, Level level) {
        BlockPos indexPos = data.selectedIndex().get();
        BlockEntity indexEntity = level.getBlockEntity(indexPos);

        if (!(indexEntity instanceof FilingIndexBlockEntity filingIndexEntity)) {
            return 0;
        }

        int maxRange = getIndexRange(filingIndexEntity);
        int successCount = 0;

        for (BlockPos cabinetPos : cabinetPositions) {
            double distance = Math.sqrt(indexPos.distSqr(cabinetPos));

            if (distance > maxRange) {
                continue;
            }

            if (data.mode() == LedgerMode.Add) {
                if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet) {
                    if (!cabinet.isInNetwork() && filingIndexEntity.addConnectedCabinet(cabinetPos)) {
                        successCount++;
                    }
                }
            } else {
                if (filingIndexEntity.removeConnectedCabinet(cabinetPos)) {
                    successCount++;
                }
            }
        }

        return successCount;
    }

    private InteractionResult performCabinetOperation(LedgerData data, BlockPos cabinetPos, Player player, Level level) {
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
            data = new LedgerData(new ArrayList<>(), Optional.empty(), LedgerMode.Add, SelectionMode.Single, Optional.empty(), Optional.empty());
        }

        data = cleanupBrokenIndices(data, level);

        if (player.isCrouching()) {
            SelectionMode newSelectionMode = data.selectionMode().toggle();
            LedgerData newData = new LedgerData(
                    data.knownIndices(),
                    data.selectedIndex(),
                    data.mode(),
                    newSelectionMode,
                    Optional.empty(),
                    Optional.empty()
            );
            ledger.set(LEDGER_DATA.value(), newData);

            player.displayClientMessage(
                    Component.translatable("message.realfilingreborn.ledger.selection_mode_changed",
                                    newSelectionMode.getName().toUpperCase())
                            .withStyle(newSelectionMode == SelectionMode.Single ? ChatFormatting.BLUE : ChatFormatting.DARK_PURPLE),
                    true
            );

            if (newSelectionMode == SelectionMode.Single) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0f, 0.7f);
            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0f, 1.3f);
            }
        } else {
            LedgerMode newMode = data.mode().toggle();
            LedgerData newData = new LedgerData(
                    data.knownIndices(),
                    data.selectedIndex(),
                    newMode,
                    data.selectionMode(),
                    Optional.empty(),
                    Optional.empty()
            );
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
        }

        return InteractionResultHolder.success(ledger);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LedgerData data = stack.get(LEDGER_DATA.value());

        if (data != null) {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.mode",
                            data.mode().getName().toUpperCase())
                    .withStyle(data.mode() == LedgerMode.Add ? ChatFormatting.GREEN : ChatFormatting.RED));

            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.selection_mode",
                            data.selectionMode().getName().toUpperCase())
                    .withStyle(data.selectionMode() == SelectionMode.Single ? ChatFormatting.BLUE : ChatFormatting.DARK_PURPLE));

            if (data.selectedIndex().isPresent()) {
                BlockPos pos = data.selectedIndex().get();
                tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.selected_index",
                                pos.getX(), pos.getY(), pos.getZ())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.no_index_selected")
                        .withStyle(ChatFormatting.GRAY));
            }

            if (data.selectionMode() == SelectionMode.Multiple) {
                if (data.firstSelectionPos().isPresent()) {
                    BlockPos pos = data.firstSelectionPos().get();
                    tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.first_corner",
                                    pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.AQUA));
                    tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.awaiting_second_corner")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                } else {
                    tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.awaiting_first_corner")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }

            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.known_indices",
                            data.knownIndices().size())
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.shift_usage")
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
            return new LedgerData(validIndices, validSelectedIndex, data.mode(), data.selectionMode(), data.firstSelectionPos(), data.secondSelectionPos());
        }

        return data;
    }
}
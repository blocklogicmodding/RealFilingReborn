package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.custom.FluidCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LedgerItem extends Item {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LinkingMode>> LINKING_MODE =
            DATA_COMPONENTS.register("linking_mode",
                    () -> DataComponentType.<LinkingMode>builder()
                            .persistent(LinkingMode.CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ActionMode>> ACTION_MODE =
            DATA_COMPONENTS.register("action_mode",
                    () -> DataComponentType.<ActionMode>builder()
                            .persistent(ActionMode.CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> FILING_INDEX_POS =
            DATA_COMPONENTS.register("filing_index_pos",
                    () -> DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> FIRST_POSITION =
            DATA_COMPONENTS.register("first_position",
                    () -> DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .build());

    public LedgerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static LinkingMode getLinkingMode(ItemStack stack) {
        return stack.getOrDefault(LINKING_MODE.value(), LinkingMode.SINGLE);
    }

    public static ActionMode getActionMode(ItemStack stack) {
        return stack.getOrDefault(ACTION_MODE.value(), ActionMode.ADD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(FILING_INDEX_POS.value());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof FilingIndexBlockEntity) {
            // Store Filing Index as controller
            stack.set(FILING_INDEX_POS.value(), pos);
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1);
            player.displayClientMessage(Component.translatable("message.realfilingreborn.ledger.index_configured")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;

        } else if ((blockEntity instanceof FilingCabinetBlockEntity || blockEntity instanceof FluidCabinetBlockEntity)
                && stack.has(FILING_INDEX_POS.value())) {

            BlockPos indexPos = stack.get(FILING_INDEX_POS.value());
            BlockEntity indexEntity = level.getBlockEntity(indexPos);

            if (indexEntity instanceof FilingIndexBlockEntity filingIndex) {
                LinkingMode linkingMode = getLinkingMode(stack);
                ActionMode actionMode = getActionMode(stack);

                if (linkingMode == LinkingMode.SINGLE) {
                    // Single cabinet connection
                    boolean success = filingIndex.addConnectedCabinet(actionMode, pos);
                    if (success) {
                        // Update cabinet's controller reference
                        if (actionMode == ActionMode.ADD) {
                            if (blockEntity instanceof FilingCabinetBlockEntity filingCabinet) {
                                // Only set if not already connected to avoid conflicts
                                if (!filingCabinet.hasController()) {
                                    filingCabinet.setControllerPos(indexPos);
                                }
                            } else if (blockEntity instanceof FluidCabinetBlockEntity fluidCabinet) {
                                // Only set if not already connected to avoid conflicts
                                if (!fluidCabinet.hasController()) {
                                    fluidCabinet.setControllerPos(indexPos);
                                }
                            }
                        } else {
                            if (blockEntity instanceof FilingCabinetBlockEntity filingCabinet) {
                                // Only clear if connected to this specific controller
                                if (indexPos.equals(filingCabinet.getControllerPos())) {
                                    filingCabinet.clearControllerPos();
                                }
                            } else if (blockEntity instanceof FluidCabinetBlockEntity fluidCabinet) {
                                // Only clear if connected to this specific controller
                                if (indexPos.equals(fluidCabinet.getControllerPos())) {
                                    fluidCabinet.clearControllerPos();
                                }
                            }
                        }

                        String messageKey = actionMode == ActionMode.ADD ?
                                "message.realfilingreborn.ledger.cabinet_linked" :
                                "message.realfilingreborn.ledger.cabinet_unlinked";
                        player.displayClientMessage(Component.translatable(messageKey)
                                .withStyle(linkingMode.getColor()), true);
                    }

                } else {
                    // Multiple cabinet connection
                    if (stack.has(FIRST_POSITION.value())) {
                        BlockPos firstPos = stack.get(FIRST_POSITION.value());
                        AABB area = new AABB(
                                Math.min(firstPos.getX(), pos.getX()),
                                Math.min(firstPos.getY(), pos.getY()),
                                Math.min(firstPos.getZ(), pos.getZ()),
                                Math.max(firstPos.getX(), pos.getX()) + 1,
                                Math.max(firstPos.getY(), pos.getY()) + 1,
                                Math.max(firstPos.getZ(), pos.getZ()) + 1
                        );

                        List<BlockPos> positions = getBlockPosInAABB(area);
                        boolean success = filingIndex.addConnectedCabinets(actionMode, positions.toArray(new BlockPos[0]));

                        if (success) {
                            // Update all cabinet controller references in the area
                            for (BlockPos cabinetPos : positions) {
                                BlockEntity cabinetEntity = level.getBlockEntity(cabinetPos);
                                if (cabinetEntity instanceof FilingCabinetBlockEntity filingCabinet) {
                                    if (actionMode == ActionMode.ADD) {
                                        filingCabinet.setControllerPos(indexPos);
                                    } else {
                                        filingCabinet.clearControllerPos();
                                    }
                                } else if (cabinetEntity instanceof FluidCabinetBlockEntity fluidCabinet) {
                                    if (actionMode == ActionMode.ADD) {
                                        fluidCabinet.setControllerPos(indexPos);
                                    } else {
                                        fluidCabinet.clearControllerPos();
                                    }
                                }
                            }

                            String messageKey = actionMode == ActionMode.ADD ?
                                    "message.realfilingreborn.ledger.multiple_cabinets_linked" :
                                    "message.realfilingreborn.ledger.multiple_cabinets_unlinked";
                            player.displayClientMessage(Component.translatable(messageKey)
                                    .withStyle(linkingMode.getColor()), true);
                        }

                        stack.remove(FIRST_POSITION.value());
                    } else {
                        // Store first position for area selection
                        stack.set(FIRST_POSITION.value(), pos);
                        player.displayClientMessage(Component.translatable("message.realfilingreborn.ledger.first_position_set")
                                .withStyle(ChatFormatting.YELLOW), true);
                    }
                }

                player.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.5f, 1);
                return InteractionResult.SUCCESS;
            }
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // Toggle linking mode (Single/Multiple)
            LinkingMode currentMode = getLinkingMode(stack);
            LinkingMode newMode = currentMode == LinkingMode.SINGLE ? LinkingMode.MULTIPLE : LinkingMode.SINGLE;
            stack.set(LINKING_MODE.value(), newMode);

            player.displayClientMessage(Component.translatable("message.realfilingreborn.ledger.mode_switched",
                            Component.translatable("ledger.realfilingreborn.mode." + newMode.name().toLowerCase(Locale.ROOT)))
                    .withStyle(newMode.getColor()), true);

            // Clear first position when switching modes
            stack.remove(FIRST_POSITION.value());

        } else {
            // Toggle action mode (Add/Remove)
            ActionMode currentAction = getActionMode(stack);
            ActionMode newAction = currentAction == ActionMode.ADD ? ActionMode.REMOVE : ActionMode.ADD;
            stack.set(ACTION_MODE.value(), newAction);

            player.displayClientMessage(Component.translatable("message.realfilingreborn.ledger.action_switched",
                            Component.translatable("ledger.realfilingreborn.action." + newAction.name().toLowerCase(Locale.ROOT)))
                    .withStyle(newAction.getColor()), true);
        }

        player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.5f, 1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LinkingMode linkingMode = getLinkingMode(stack);
        ActionMode actionMode = getActionMode(stack);

        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.mode")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("ledger.realfilingreborn.mode." + linkingMode.name().toLowerCase(Locale.ROOT))
                        .withStyle(linkingMode.getColor())));

        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.action")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("ledger.realfilingreborn.action." + actionMode.name().toLowerCase(Locale.ROOT))
                        .withStyle(actionMode.getColor())));

        if (stack.has(FILING_INDEX_POS.value())) {
            BlockPos pos = stack.get(FILING_INDEX_POS.value());
            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.index_location")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(ChatFormatting.DARK_AQUA)));
        } else {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.no_index")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("tooltip.realfilingreborn.ledger.usage")
                .withStyle(ChatFormatting.GRAY));

        super.appendHoverText(stack, context, tooltip, flag);
    }

    private List<BlockPos> getBlockPosInAABB(AABB aabb) {
        List<BlockPos> blocks = new ArrayList<>();
        for (double y = aabb.minY; y < aabb.maxY; ++y) {
            for (double x = aabb.minX; x < aabb.maxX; ++x) {
                for (double z = aabb.minZ; z < aabb.maxZ; ++z) {
                    blocks.add(new BlockPos((int) x, (int) y, (int) z));
                }
            }
        }
        return blocks;
    }

    public enum LinkingMode implements StringRepresentable {
        SINGLE(ChatFormatting.DARK_AQUA),
        MULTIPLE(ChatFormatting.GREEN);

        public static final Codec<LinkingMode> CODEC = StringRepresentable.fromValues(LinkingMode::values);

        private final ChatFormatting color;

        LinkingMode(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting getColor() {
            return color;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ActionMode implements StringRepresentable {
        ADD(ChatFormatting.BLUE),
        REMOVE(ChatFormatting.GOLD);

        public static final Codec<ActionMode> CODEC = StringRepresentable.fromValues(ActionMode::values);

        private final ChatFormatting color;

        ActionMode(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting getColor() {
            return color;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
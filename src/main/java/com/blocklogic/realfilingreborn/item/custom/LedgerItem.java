package com.blocklogic.realfilingreborn.item.custom;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.custom.FluidCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.component.LedgerData;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LedgerItem extends Item {

    public LedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            toggleOperationMode(stack, player);
        } else {
            toggleSelectionMode(stack, player);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player == null) return InteractionResult.FAIL;

        LedgerData data = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        // Handle Filing Index selection
        if (state.getBlock() instanceof FilingIndexBlock && player.isShiftKeyDown()) {
            selectController(stack, pos, player);
            return InteractionResult.SUCCESS;
        }

        // Handle Cabinet linking
        if ((state.getBlock() instanceof FilingCabinetBlock || state.getBlock() instanceof FluidCabinetBlock) && player.isShiftKeyDown()) {
            if (data.selectionMode() == LedgerData.SelectionMode.SINGLE) {
                handleSingleCabinetAction(level, pos, stack, player);
            } else {
                handleMultiCabinetAction(level, pos, stack, player);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void toggleOperationMode(ItemStack stack, Player player) {
        LedgerData currentData = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        LedgerData.OperationMode newMode = currentData.operationMode() == LedgerData.OperationMode.ADD
                ? LedgerData.OperationMode.REMOVE
                : LedgerData.OperationMode.ADD;

        LedgerData newData = currentData.withOperationMode(newMode);
        stack.set(ModDataComponents.LEDGER_DATA.get(), newData);

        Component message = newMode == LedgerData.OperationMode.ADD
                ? Component.translatable("item.realfilingreborn.ledger.mode.add")
                : Component.translatable("item.realfilingreborn.ledger.mode.remove");

        player.displayClientMessage(message, true);
    }

    private void toggleSelectionMode(ItemStack stack, Player player) {
        LedgerData currentData = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        LedgerData.SelectionMode newMode = currentData.selectionMode() == LedgerData.SelectionMode.SINGLE
                ? LedgerData.SelectionMode.MULTI
                : LedgerData.SelectionMode.SINGLE;

        LedgerData newData = currentData.withSelectionMode(newMode);
        stack.set(ModDataComponents.LEDGER_DATA.get(), newData);

        Component message = newMode == LedgerData.SelectionMode.SINGLE
                ? Component.translatable("item.realfilingreborn.ledger.selection.single")
                : Component.translatable("item.realfilingreborn.ledger.selection.multi");

        player.displayClientMessage(message, true);
    }

    private void selectController(ItemStack stack, BlockPos controllerPos, Player player) {
        LedgerData currentData = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);
        LedgerData newData = currentData.withSelectedController(controllerPos);
        stack.set(ModDataComponents.LEDGER_DATA.get(), newData);

        Component message = Component.translatable("item.realfilingreborn.ledger.controller.selected",
                controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
        player.displayClientMessage(message, true);
    }

    private void handleSingleCabinetAction(Level level, BlockPos cabinetPos, ItemStack stack, Player player) {
        LedgerData data = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        if (data.selectedController() == null) {
            Component message = Component.translatable("item.realfilingreborn.ledger.error.no_controller");
            player.displayClientMessage(message, true);
            return;
        }

        // Check if controller exists and is in range
        if (!(level.getBlockEntity(data.selectedController()) instanceof FilingIndexBlockEntity indexEntity)) {
            Component message = Component.translatable("item.realfilingreborn.ledger.error.no_controller");
            player.displayClientMessage(message, true);
            return;
        }

        // Check range
        if (!isInRange(data.selectedController(), cabinetPos, indexEntity.getRange())) {
            Component message = Component.translatable("item.realfilingreborn.ledger.cabinet.out_of_range");
            player.displayClientMessage(message, true);
            return;
        }

        // Handle Filing Cabinet
        if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinetEntity) {
            if (data.operationMode() == LedgerData.OperationMode.ADD) {
                cabinetEntity.setControllerPos(data.selectedController());
                indexEntity.addCabinet(cabinetPos);

                Component message = Component.translatable("item.realfilingreborn.ledger.cabinet.linked");
                player.displayClientMessage(message, true);
            } else {
                BlockPos oldControllerPos = cabinetEntity.getControllerPos();
                cabinetEntity.clearControllerPos();

                if (oldControllerPos != null && level.getBlockEntity(oldControllerPos) instanceof FilingIndexBlockEntity oldIndex) {
                    oldIndex.removeCabinet(cabinetPos);
                }

                Component message = Component.translatable("item.realfilingreborn.ledger.cabinet.unlinked");
                player.displayClientMessage(message, true);
            }
        }
        // Handle Fluid Cabinet
        else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinetEntity) {
            if (data.operationMode() == LedgerData.OperationMode.ADD) {
                fluidCabinetEntity.setControllerPos(data.selectedController());
                indexEntity.addCabinet(cabinetPos);

                Component message = Component.translatable("item.realfilingreborn.ledger.cabinet.linked");
                player.displayClientMessage(message, true);
            } else {
                BlockPos oldControllerPos = fluidCabinetEntity.getControllerPos();
                fluidCabinetEntity.clearControllerPos();

                if (oldControllerPos != null && level.getBlockEntity(oldControllerPos) instanceof FilingIndexBlockEntity oldIndex) {
                    oldIndex.removeCabinet(cabinetPos);
                }

                Component message = Component.translatable("item.realfilingreborn.ledger.cabinet.unlinked");
                player.displayClientMessage(message, true);
            }
        }
    }

    private void handleMultiCabinetAction(Level level, BlockPos cabinetPos, ItemStack stack, Player player) {
        LedgerData data = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        if (data.firstMultiPos() == null) {
            LedgerData newData = data.withFirstMultiPos(cabinetPos);
            stack.set(ModDataComponents.LEDGER_DATA.get(), newData);

            Component message = Component.translatable("item.realfilingreborn.ledger.multi.start",
                    cabinetPos.getX(), cabinetPos.getY(), cabinetPos.getZ());
            player.displayClientMessage(message, true);
        } else {
            processMultiSelection(level, data.firstMultiPos(), cabinetPos, stack, player);

            LedgerData newData = data.withFirstMultiPos(null);
            stack.set(ModDataComponents.LEDGER_DATA.get(), newData);
        }
    }

    // PERFORMANCE: Optimized multi-selection with batch operations
    private void processMultiSelection(Level level, BlockPos pos1, BlockPos pos2, ItemStack stack, Player player) {
        LedgerData data = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        if (data.selectedController() == null && data.operationMode() == LedgerData.OperationMode.ADD) {
            Component message = Component.translatable("item.realfilingreborn.ledger.error.no_controller");
            player.displayClientMessage(message, true);
            return;
        }

        FilingIndexBlockEntity indexEntity = null;
        if (data.selectedController() != null && level.getBlockEntity(data.selectedController()) instanceof FilingIndexBlockEntity index) {
            indexEntity = index;
        }

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        // PERFORMANCE: Collect cabinets in batches for bulk operations
        Set<BlockPos> cabinetsToAdd = new HashSet<>();
        Set<BlockPos> cabinetsToRemove = new HashSet<>();
        int processedCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(currentPos);

                    if (state.getBlock() instanceof FilingCabinetBlock || state.getBlock() instanceof FluidCabinetBlock) {
                        // Check range for ADD operations
                        if (data.operationMode() == LedgerData.OperationMode.ADD && indexEntity != null) {
                            if (!isInRange(data.selectedController(), currentPos, indexEntity.getRange())) {
                                continue; // Skip out of range cabinets
                            }
                        }

                        boolean processed = false;

                        // Handle Filing Cabinet
                        if (level.getBlockEntity(currentPos) instanceof FilingCabinetBlockEntity cabinetEntity) {
                            if (data.operationMode() == LedgerData.OperationMode.ADD) {
                                cabinetEntity.setControllerPos(data.selectedController());
                                cabinetsToAdd.add(currentPos);
                            } else {
                                BlockPos oldControllerPos = cabinetEntity.getControllerPos();
                                cabinetEntity.clearControllerPos();

                                if (oldControllerPos != null) {
                                    cabinetsToRemove.add(currentPos);
                                }
                            }
                            processed = true;
                        }
                        // Handle Fluid Cabinet
                        else if (level.getBlockEntity(currentPos) instanceof FluidCabinetBlockEntity fluidCabinetEntity) {
                            if (data.operationMode() == LedgerData.OperationMode.ADD) {
                                fluidCabinetEntity.setControllerPos(data.selectedController());
                                cabinetsToAdd.add(currentPos);
                            } else {
                                BlockPos oldControllerPos = fluidCabinetEntity.getControllerPos();
                                fluidCabinetEntity.clearControllerPos();

                                if (oldControllerPos != null) {
                                    cabinetsToRemove.add(currentPos);
                                }
                            }
                            processed = true;
                        }

                        if (processed) {
                            processedCount++;
                        }
                    }
                }
            }
        }

        // PERFORMANCE: Batch update operations to reduce block state changes
        if (!cabinetsToAdd.isEmpty() && indexEntity != null) {
            indexEntity.addCabinets(cabinetsToAdd);
        }

        if (!cabinetsToRemove.isEmpty()) {
            // FIXED: Simple approach - remove cabinets individually for now
            // The cabinet entities already cleared their controller pos above
            for (BlockPos cabinetPos : cabinetsToRemove) {
                // Try to find any controller that has this cabinet linked
                // Since we're in a limited area, scan nearby blocks for filing indexes
                for (int dx = -64; dx <= 64; dx++) {
                    for (int dy = -64; dy <= 64; dy++) {
                        for (int dz = -64; dz <= 64; dz++) {
                            BlockPos checkPos = cabinetPos.offset(dx, dy, dz);
                            if (level.getBlockEntity(checkPos) instanceof FilingIndexBlockEntity oldIndex) {
                                if (oldIndex.getLinkedCabinets().contains(cabinetPos)) {
                                    oldIndex.removeCabinet(cabinetPos);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        Component message = data.operationMode() == LedgerData.OperationMode.ADD
                ? Component.translatable("item.realfilingreborn.ledger.multi.linked", processedCount)
                : Component.translatable("item.realfilingreborn.ledger.multi.unlinked", processedCount);

        player.displayClientMessage(message, true);
    }

    private boolean isInRange(BlockPos controllerPos, BlockPos cabinetPos, int range) {
        // PERFORMANCE: Use squared distance to avoid expensive sqrt
        double distSq = controllerPos.distSqr(cabinetPos);
        double rangeSq = (double) range * range;
        return distSq <= rangeSq;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.realfilingreborn.ledger.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE));

        LedgerData data = stack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        Component operationText = data.operationMode() == LedgerData.OperationMode.ADD
                ? Component.translatable("item.realfilingreborn.ledger.tooltip.operation.add").withColor(0x00FF00)
                : Component.translatable("item.realfilingreborn.ledger.tooltip.operation.remove").withColor(0xFF5555);
        tooltip.add(operationText);

        Component selectionText = data.selectionMode() == LedgerData.SelectionMode.SINGLE
                ? Component.translatable("item.realfilingreborn.ledger.tooltip.selection.single").withColor(0x5555FF)
                : Component.translatable("item.realfilingreborn.ledger.tooltip.selection.multi").withColor(0xFF55FF);
        tooltip.add(selectionText);

        if (data.selectedController() != null) {
            Component controllerText = Component.translatable("item.realfilingreborn.ledger.tooltip.controller.selected",
                            data.selectedController().getX(), data.selectedController().getY(), data.selectedController().getZ())
                    .withColor(0x00FFFF);
            tooltip.add(controllerText);
        } else {
            Component noControllerText = Component.translatable("item.realfilingreborn.ledger.tooltip.controller.none")
                    .withColor(0xFFAA00);
            tooltip.add(noControllerText);
        }

        if (data.firstMultiPos() != null) {
            Component multiText = Component.translatable("item.realfilingreborn.ledger.tooltip.multi.active",
                            data.firstMultiPos().getX(), data.firstMultiPos().getY(), data.firstMultiPos().getZ())
                    .withColor(0xFFFF00);
            tooltip.add(multiText);
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.realfilingreborn.ledger.tooltip.usage.selection").withColor(0xAAAAAA));
        tooltip.add(Component.translatable("item.realfilingreborn.ledger.tooltip.usage.operation").withColor(0xAAAAAA));
        tooltip.add(Component.translatable("item.realfilingreborn.ledger.tooltip.usage.controller").withColor(0xAAAAAA));
        tooltip.add(Component.translatable("item.realfilingreborn.ledger.tooltip.usage.cabinet").withColor(0xAAAAAA));
    }
}
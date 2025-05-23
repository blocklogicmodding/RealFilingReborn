package com.blocklogic.realfilingreborn.client.renderer;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeDiamondItem;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeIronItem;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeNetheriteItem;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "realfilingreborn", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class LedgerWireframeRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;

        if (player == null || level == null) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof LedgerItem)) {
            return;
        }

        LedgerItem.LedgerData ledgerData = mainHand.get(LedgerItem.LEDGER_DATA.value());
        if (ledgerData == null || ledgerData.selectedIndex().isEmpty()) {
            return;
        }

        BlockPos selectedIndexPos = ledgerData.selectedIndex().get();
        BlockEntity indexEntity = level.getBlockEntity(selectedIndexPos);

        if (!(indexEntity instanceof FilingIndexBlockEntity filingIndex)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        renderWireframeBox(poseStack, bufferSource, selectedIndexPos, 1.0f, 1.0f, 1.0f, 1.0f);

        for (BlockPos cabinetPos : filingIndex.getConnectedCabinets()) {
            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity) {
                renderWireframeBox(poseStack, bufferSource, cabinetPos, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        int range = getIndexRange(filingIndex);
        renderRangeBox(poseStack, bufferSource, selectedIndexPos, range, 0.0f, 1.0f, 0.0f, 0.5f);

        if (ledgerData.selectionMode() == LedgerItem.SelectionMode.Multiple) {
            renderSelectionArea(poseStack, bufferSource, ledgerData, level, minecraft);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderSelectionArea(PoseStack poseStack, MultiBufferSource bufferSource,
                                            LedgerItem.LedgerData ledgerData, Level level, Minecraft minecraft) {
        if (ledgerData.firstSelectionPos().isPresent()) {
            BlockPos firstCorner = ledgerData.firstSelectionPos().get();

            renderWireframeBox(poseStack, bufferSource, firstCorner, 0.0f, 1.0f, 1.0f, 1.0f);

            if (ledgerData.secondSelectionPos().isPresent()) {
                BlockPos secondCorner = ledgerData.secondSelectionPos().get();
                renderSelectionAreaBox(poseStack, bufferSource, firstCorner, secondCorner, level);
            } else {
                HitResult hitResult = minecraft.hitResult;
                if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hitResult;
                    BlockPos mousePos = blockHit.getBlockPos();

                    if (level.getBlockState(mousePos).getBlock() instanceof FilingCabinetBlock) {
                        renderSelectionPreview(poseStack, bufferSource, firstCorner, mousePos, level);
                    }
                }
            }
        }
    }

    private static void renderSelectionPreview(PoseStack poseStack, MultiBufferSource bufferSource,
                                               BlockPos firstCorner, BlockPos mousePos, Level level) {
        int minX = Math.min(firstCorner.getX(), mousePos.getX());
        int maxX = Math.max(firstCorner.getX(), mousePos.getX());
        int minY = Math.min(firstCorner.getY(), mousePos.getY());
        int maxY = Math.max(firstCorner.getY(), mousePos.getY());
        int minZ = Math.min(firstCorner.getZ(), mousePos.getZ());
        int maxZ = Math.max(firstCorner.getZ(), mousePos.getZ());

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, vertexConsumer,
                minX, minY, minZ,
                maxX + 1, maxY + 1, maxZ + 1,
                1.0f, 0.8f, 0.0f, 0.6f);

        int cabinetCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof FilingCabinetBlock &&
                            level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity) {
                        renderWireframeBox(poseStack, bufferSource, pos, 1.0f, 1.0f, 0.0f, 0.8f);
                        cabinetCount++;
                    }
                }
            }
        }

        if (level.getBlockState(mousePos).getBlock() instanceof FilingCabinetBlock) {
            renderWireframeBox(poseStack, bufferSource, mousePos, 1.0f, 0.5f, 0.0f, 1.0f);
        }
    }

    private static void renderSelectionAreaBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                               BlockPos corner1, BlockPos corner2, Level level) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, vertexConsumer,
                minX, minY, minZ,
                maxX + 1, maxY + 1, maxZ + 1,
                1.0f, 0.0f, 1.0f, 0.8f);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof FilingCabinetBlock &&
                            level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity) {
                        renderWireframeBox(poseStack, bufferSource, pos, 0.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                           BlockPos pos, float red, float green, float blue, float alpha) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        AABB aabb = new AABB(pos);

        LevelRenderer.renderLineBox(poseStack, vertexConsumer,
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ,
                red, green, blue, alpha);
    }

    private static void renderRangeBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                       BlockPos centerPos, int range, float red, float green, float blue, float alpha) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        double minX = centerPos.getX() - range + 0.5;
        double minY = centerPos.getY() - range + 0.5;
        double minZ = centerPos.getZ() - range + 0.5;
        double maxX = centerPos.getX() + range + 0.5;
        double maxY = centerPos.getY() + range + 0.5;
        double maxZ = centerPos.getZ() + range + 0.5;

        LevelRenderer.renderLineBox(poseStack, vertexConsumer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                red, green, blue, alpha);
    }

    private static int getIndexRange(FilingIndexBlockEntity indexEntity) {
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
}
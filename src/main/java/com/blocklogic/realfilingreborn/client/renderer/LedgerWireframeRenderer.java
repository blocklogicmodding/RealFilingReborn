package com.blocklogic.realfilingreborn.client.renderer;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeDiamondItem;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeIronItem;
import com.blocklogic.realfilingreborn.item.custom.IndexRangeUpgradeNetheriteItem;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
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

        poseStack.popPose();

        bufferSource.endBatch();
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
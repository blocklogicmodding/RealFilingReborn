package com.blocklogic.realfilingreborn.client.renderer;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.component.LedgerData;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Set;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT)
public class LedgerWireframeRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;

        if (player == null || level == null) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        ItemStack ledgerStack = null;
        if (mainHand.getItem() instanceof LedgerItem) {
            ledgerStack = mainHand;
        } else if (offHand.getItem() instanceof LedgerItem) {
            ledgerStack = offHand;
        }

        if (ledgerStack == null) {
            return;
        }

        LedgerData data = ledgerStack.getOrDefault(ModDataComponents.LEDGER_DATA.get(), LedgerData.DEFAULT);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        if (data.selectedController() != null) {
            renderIndexWireframe(poseStack, cameraPos, data.selectedController(), level);
        }

        if (data.selectedController() != null && level.getBlockEntity(data.selectedController()) instanceof FilingIndexBlockEntity indexEntity) {
            renderConnectedCabinetsWireframe(poseStack, cameraPos, indexEntity.getLinkedCabinets(), level);
            renderRangeWireframe(poseStack, cameraPos, data.selectedController(), indexEntity.getRange());
        }

        if (data.firstMultiPos() != null && data.selectionMode() == LedgerData.SelectionMode.MULTI) {
            renderMultiSelectionWireframe(poseStack, cameraPos, data.firstMultiPos(), level, minecraft);
        }
    }

    private static void renderIndexWireframe(PoseStack poseStack, Vec3 cameraPos, BlockPos indexPos, Level level) {
        if (!(level.getBlockEntity(indexPos) instanceof FilingIndexBlockEntity)) {
            return;
        }

        AABB aabb = new AABB(indexPos);
        renderWireframeBox(poseStack, cameraPos, aabb, 0.0f, 1.0f, 0.0f, 0.8f);
    }

    private static void renderConnectedCabinetsWireframe(PoseStack poseStack, Vec3 cameraPos, Set<BlockPos> linkedCabinets, Level level) {
        for (BlockPos cabinetPos : linkedCabinets) {
            boolean isValidCabinet = false;

            if (level.getBlockEntity(cabinetPos) instanceof FilingCabinetBlockEntity cabinet && cabinet.isLinkedToController()) {
                isValidCabinet = true;
            } else if (level.getBlockEntity(cabinetPos) instanceof FluidCabinetBlockEntity fluidCabinet && fluidCabinet.isLinkedToController()) {
                isValidCabinet = true;
            }

            if (isValidCabinet) {
                AABB aabb = new AABB(cabinetPos);
                renderWireframeBox(poseStack, cameraPos, aabb, 1.0f, 1.0f, 1.0f, 0.6f);
            }
        }
    }

    private static void renderRangeWireframe(PoseStack poseStack, Vec3 cameraPos, BlockPos indexPos, int range) {
        double minX = indexPos.getX() - range;
        double maxX = indexPos.getX() + range + 1.0;
        double minY = indexPos.getY() - range;
        double maxY = indexPos.getY() + range + 1.0;
        double minZ = indexPos.getZ() - range;
        double maxZ = indexPos.getZ() + range + 1.0;

        AABB rangeAABB = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        renderWireframeBox(poseStack, cameraPos, rangeAABB, 1.0f, 1.0f, 0.0f, 0.3f);
    }

    private static void renderMultiSelectionWireframe(PoseStack poseStack, Vec3 cameraPos, BlockPos firstPos, Level level, Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos currentPos = blockHitResult.getBlockPos();

            int minX = Math.min(firstPos.getX(), currentPos.getX());
            int maxX = Math.max(firstPos.getX(), currentPos.getX());
            int minY = Math.min(firstPos.getY(), currentPos.getY());
            int maxY = Math.max(firstPos.getY(), currentPos.getY());
            int minZ = Math.min(firstPos.getZ(), currentPos.getZ());
            int maxZ = Math.max(firstPos.getZ(), currentPos.getZ());

            AABB selectionArea = new AABB(
                    minX,
                    minY,
                    minZ,
                    maxX + 1.0,
                    maxY + 1.0,
                    maxZ + 1.0
            );
            renderWireframeBox(poseStack, cameraPos, selectionArea, 1.0f, 1.0f, 1.0f, 0.6f);
        }
    }

    private static void renderWireframeBox(PoseStack poseStack, Vec3 cameraPos, AABB aabb, float red, float green, float blue, float alpha) {
        poseStack.pushPose();

        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());

        LevelRenderer.renderLineBox(poseStack, buffer, aabb, red, green, blue, alpha);

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
package com.blocklogic.realfilingreborn.client.renderer;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.LedgerItem;
import com.blocklogic.realfilingreborn.util.ConnectedCabinets;
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

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "realfilingreborn", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class NetworkWireframeRenderer {

    private static final float[] WHITE_SOLID = {1.0f, 1.0f, 1.0f, 1.0f};
    private static final float[] GREEN = {0.0f, 1.0f, 0.0f, 1.0f};
    private static final float[] AQUA = {0.0f, 1.0f, 1.0f, 1.0f};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Check if player is holding a Ledger item
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        ItemStack ledgerStack = null;
        if (mainHand.getItem() instanceof LedgerItem) {
            ledgerStack = mainHand;
        } else if (offHand.getItem() instanceof LedgerItem) {
            ledgerStack = offHand;
        }

        if (ledgerStack == null) return;

        // Check if ledger has a Filing Index configured
        if (!ledgerStack.has(LedgerItem.FILING_INDEX_POS.value())) return;

        BlockPos indexPos = ledgerStack.get(LedgerItem.FILING_INDEX_POS.value());
        Level level = player.level();
        BlockEntity indexEntity = level.getBlockEntity(indexPos);

        if (!(indexEntity instanceof FilingIndexBlockEntity filingIndex)) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // 1. Render Filing Index range (white bounding box + translucent quads)
        renderIndexRange(poseStack, bufferSource, indexPos, filingIndex.getConnectionRange());

        // 2. Render selected Filing Index (green wireframe)
        renderIndexWireframe(poseStack, bufferSource, indexPos);

        // 3. Render connected cabinets (aqua wireframes)
        renderConnectedCabinets(poseStack, bufferSource, filingIndex, level);

        // 4. Render multi-select area if in multiple mode
        renderMultiSelectArea(poseStack, bufferSource, ledgerStack, level, player);

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderIndexRange(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos indexPos, int range) {
        // Create AABB for the range
        AABB rangeBox = new AABB(indexPos).inflate(range);

        // Render wireframe outline only (no translucent quads to avoid UV issues)
        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, linesConsumer, rangeBox, WHITE_SOLID[0], WHITE_SOLID[1], WHITE_SOLID[2], WHITE_SOLID[3]);
    }

    private static void renderIndexWireframe(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos indexPos) {
        AABB blockBox = new AABB(indexPos);
        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, linesConsumer, blockBox, GREEN[0], GREEN[1], GREEN[2], GREEN[3]);
    }

    private static void renderConnectedCabinets(PoseStack poseStack, MultiBufferSource bufferSource, FilingIndexBlockEntity filingIndex, Level level) {
        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());

        List<Long> connectedCabinets = filingIndex.getConnectedCabinets().getConnectedCabinets();
        for (Long cabinetLong : connectedCabinets) {
            BlockPos cabinetPos = BlockPos.of(cabinetLong);
            BlockEntity entity = level.getBlockEntity(cabinetPos);

            if (entity instanceof FilingCabinetBlockEntity || entity instanceof FluidCabinetBlockEntity) {
                AABB blockBox = new AABB(cabinetPos);
                LevelRenderer.renderLineBox(poseStack, linesConsumer, blockBox, AQUA[0], AQUA[1], AQUA[2], AQUA[3]);
            }
        }
    }

    private static void renderMultiSelectArea(PoseStack poseStack, MultiBufferSource bufferSource, ItemStack ledgerStack, Level level, Player player) {
        LedgerItem.LinkingMode mode = LedgerItem.getLinkingMode(ledgerStack);
        if (mode != LedgerItem.LinkingMode.MULTIPLE) return;

        if (!ledgerStack.has(LedgerItem.FIRST_POSITION.value())) return;

        BlockPos firstPos = ledgerStack.get(LedgerItem.FIRST_POSITION.value());
        BlockPos currentPos = player.blockPosition();

        // Create AABB from first position to current position
        AABB selectionArea = new AABB(
                Math.min(firstPos.getX(), currentPos.getX()),
                Math.min(firstPos.getY(), currentPos.getY()),
                Math.min(firstPos.getZ(), currentPos.getZ()),
                Math.max(firstPos.getX(), currentPos.getX()) + 1,
                Math.max(firstPos.getY(), currentPos.getY()) + 1,
                Math.max(firstPos.getZ(), currentPos.getZ()) + 1
        );

        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());

        // Render wireframes for each cabinet in the selection area
        List<BlockPos> positions = getBlockPosInAABB(selectionArea);
        for (BlockPos pos : positions) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof FilingCabinetBlockEntity || entity instanceof FluidCabinetBlockEntity) {
                AABB blockBox = new AABB(pos);
                LevelRenderer.renderLineBox(poseStack, linesConsumer, blockBox, WHITE_SOLID[0], WHITE_SOLID[1], WHITE_SOLID[2], WHITE_SOLID[3]);
            }
        }
    }



    private static List<BlockPos> getBlockPosInAABB(AABB aabb) {
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
}
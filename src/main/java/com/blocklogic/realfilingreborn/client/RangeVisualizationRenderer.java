package com.blocklogic.realfilingreborn.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class RangeVisualizationRenderer {
    private static final RangeVisualizationManager manager = new RangeVisualizationManager();

    private static final float RED = 0.9F;
    private static final float GREEN = 0.2F;
    private static final float BLUE = 0.3F;
    private static final float ALPHA = 0.4F;
    private static final float LINE_WIDTH = 2.0F;

    public static void init() {
        // Register for rendering events
        NeoForge.EVENT_BUS.register(RangeVisualizationRenderer.class);
    }

    public static void toggleRangeVisualization(BlockPos pos, int range) {
        manager.toggleRangeVisualization(pos, range);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Stage check and early return
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        // Get active visualizations from manager
        Map<BlockPos, Integer> visualizations = RangeVisualizationManager.getActiveVisualizations();
        if (visualizations.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Get buffer source once per frame
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        for (Map.Entry<BlockPos, Integer> entry : visualizations.entrySet()) {
            BlockPos pos = entry.getKey();
            int range = entry.getValue();

            // Create bounding box with proper expansion
            AABB box = new AABB(pos)
                    .inflate(range + 0.5)  // Proper cube expansion
                    .move(0.5, 0.5, 0.5);  // Center on block

            // Get line buffer
            VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());

            // Render with proper line width
            RenderSystem.lineWidth(3.0F);
            LevelRenderer.renderLineBox(poseStack, builder, box, RED, GREEN, BLUE, ALPHA);
        }

        // End batch after all rendering
        bufferSource.endBatch();
        poseStack.popPose();
    }

}
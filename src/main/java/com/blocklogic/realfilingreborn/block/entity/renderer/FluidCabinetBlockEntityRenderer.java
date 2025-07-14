package com.blocklogic.realfilingreborn.block.entity.renderer;

import com.blocklogic.realfilingreborn.block.custom.FluidCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FluidCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FluidCanisterItem;
import com.blocklogic.realfilingreborn.util.FormattingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class FluidCabinetBlockEntityRenderer implements BlockEntityRenderer<FluidCabinetBlockEntity> {

    private static final Map<Fluid, IClientFluidTypeExtensions> FLUID_EXTENSIONS_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, TextureAtlasSprite> SPRITE_CACHE = new ConcurrentHashMap<>();
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Font FONT = MC.font;

    private static final float[][] POSITIONS = {
            {-0.188f, 0.188f},
            {0.188f, 0.188f},
            {-0.188f, -0.188f},
            {0.188f, -0.188f}
    };

    public FluidCabinetBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidCabinetBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(FluidCabinetBlock.FACING);

        int itemLight = 0xF000F0;

        poseStack.pushPose();

        setupFaceTransform(poseStack, facing);

        renderFluidGrid(blockEntity, poseStack, bufferSource, itemLight);

        poseStack.popPose();
    }

    private void setupFaceTransform(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));

        switch (facing) {
            case NORTH -> poseStack.translate(0, 0, 0.475);
            case EAST -> {
                poseStack.translate(-0.475, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case SOUTH -> {
                poseStack.translate(0, 0, -0.475);
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            case WEST -> {
                poseStack.translate(0.475, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
        }

        poseStack.translate(0, 0, -0.5/16D);
    }

    private void renderFluidGrid(FluidCabinetBlockEntity blockEntity, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int itemLight) {

        float quadWidth = 0.25f;
        float quadHeight = 0.25f;

        for (int slot = 0; slot < Math.min(4, blockEntity.inventory.getSlots()); slot++) {
            ItemStack canisterStack = blockEntity.inventory.getStackInSlot(slot);

            if (!canisterStack.isEmpty() && canisterStack.getItem() instanceof FluidCanisterItem) {
                FluidCanisterItem.CanisterContents contents = canisterStack.get(FluidCanisterItem.CANISTER_CONTENTS.value());

                if (contents != null && contents.storedFluidId().isPresent() && contents.amount() > 0) {
                    ResourceLocation fluidId = contents.storedFluidId().get();
                    Fluid fluid = getFluidFromId(fluidId);

                    if (fluid != null && fluid != Fluids.EMPTY) {
                        float offsetX = POSITIONS[slot][0];
                        float offsetY = POSITIONS[slot][1];

                        renderFluidQuad(fluid, contents.amount(), offsetX, offsetY, quadWidth, quadHeight,
                                poseStack, bufferSource, itemLight);

                        String formattedAmount = FormattingCache.getFormattedFluidAmount(contents.amount());
                        renderFluidText(formattedAmount, offsetX, offsetY - quadHeight/2f - 0.025f,
                                poseStack, bufferSource, itemLight);
                    }
                }
            }
        }
    }

    private void renderFluidQuad(Fluid fluid, int amount, float offsetX, float offsetY,
                                 float width, float height, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        try {
            IClientFluidTypeExtensions fluidExtensions = FLUID_EXTENSIONS_CACHE.computeIfAbsent(fluid,
                    IClientFluidTypeExtensions::of);
            ResourceLocation stillTexture = fluidExtensions.getStillTexture();

            if (stillTexture != null) {
                TextureAtlasSprite sprite = SPRITE_CACHE.computeIfAbsent(stillTexture, texture ->
                        MC.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture));

                VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
                Matrix4f matrix = poseStack.last().pose();

                FluidStack fluidStack = new FluidStack(fluid, 1000);
                int color = fluidExtensions.getTintColor(fluidStack);
                float red = ((color >> 16) & 0xFF) / 255f;
                float green = ((color >> 8) & 0xFF) / 255f;
                float blue = (color & 0xFF) / 255f;
                float alpha = ((color >> 24) & 0xFF) / 255f;
                if (alpha == 0) alpha = 1.0f;

                float maxVisualAmount = 100000000;
                float fillPercentage = Math.min(amount / maxVisualAmount, 1.0f);

                float halfWidth = width / 2f;
                float halfHeight = height / 2f;
                float quadLeft = offsetX - halfWidth;
                float quadRight = offsetX + halfWidth;
                float quadBottom = offsetY - halfHeight;
                float quadTop = offsetY + halfHeight;

                float fillHeight = height * fillPercentage;
                float fluidTop = quadBottom + fillHeight;

                float minU = sprite.getU0();
                float maxU = sprite.getU1();
                float minV = sprite.getV0();
                float maxV = sprite.getV1();

                if (fillPercentage > 0) {
                    float fillVRange = (maxV - minV) * fillPercentage;
                    float adjustedMinV = maxV - fillVRange;

                    consumer.addVertex(matrix, quadLeft, quadBottom, 0.001f)
                            .setColor(red, green, blue, alpha)
                            .setUv(minU, maxV)
                            .setLight(packedLight)
                            .setNormal(0, 0, 1);

                    consumer.addVertex(matrix, quadRight, quadBottom, 0.001f)
                            .setColor(red, green, blue, alpha)
                            .setUv(maxU, maxV)
                            .setLight(packedLight)
                            .setNormal(0, 0, 1);

                    consumer.addVertex(matrix, quadRight, fluidTop, 0.001f)
                            .setColor(red, green, blue, alpha)
                            .setUv(maxU, adjustedMinV)
                            .setLight(packedLight)
                            .setNormal(0, 0, 1);

                    consumer.addVertex(matrix, quadLeft, fluidTop, 0.001f)
                            .setColor(red, green, blue, alpha)
                            .setUv(minU, adjustedMinV)
                            .setLight(packedLight)
                            .setNormal(0, 0, 1);
                }
            }
        } catch (Exception e) {
            renderSolidQuad(0.2f, 0.5f, 1.0f, 0.8f, amount, offsetX, offsetY, width, height,
                    poseStack, bufferSource, packedLight);
        }
    }

    private void renderSolidQuad(float red, float green, float blue, float alpha, int amount,
                                 float offsetX, float offsetY, float width, float height,
                                 PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        Matrix4f matrix = poseStack.last().pose();

        float maxVisualAmount = 100000000;
        float fillPercentage = Math.min(amount / maxVisualAmount, 1.0f);

        if (fillPercentage > 0) {
            float halfWidth = width / 2f;
            float halfHeight = height / 2f;
            float quadLeft = offsetX - halfWidth;
            float quadRight = offsetX + halfWidth;
            float quadBottom = offsetY - halfHeight;

            float fillHeight = height * fillPercentage;
            float fluidTop = quadBottom + fillHeight;

            consumer.addVertex(matrix, quadLeft, quadBottom, 0.001f)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 1)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            consumer.addVertex(matrix, quadRight, quadBottom, 0.001f)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 1)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            consumer.addVertex(matrix, quadRight, fluidTop, 0.001f)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 0)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            consumer.addVertex(matrix, quadLeft, fluidTop, 0.001f)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 0)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);
        }
    }

    private void renderFluidText(String text, float offsetX, float offsetY, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        if (text.isEmpty()) return;

        poseStack.pushPose();

        poseStack.translate(offsetX, offsetY, 0.002f);

        poseStack.scale(0.004f, 0.004f, 0.004f);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        int textWidth = FONT.width(text);
        float xOffset = -textWidth / 2.0f;

        FONT.drawInBatch(
                text,
                xOffset,
                0,
                0xFFFFFF,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );

        poseStack.popPose();
    }

    private Fluid getFluidFromId(ResourceLocation fluidId) {
        try {
            return BuiltInRegistries.FLUID.get(fluidId);
        } catch (Exception e) {
            return Fluids.EMPTY;
        }
    }
}
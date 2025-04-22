package com.blocklogic.realfilingreborn.block.entity.renderer;

import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public class FilingCabinetBlockEntityRenderer implements BlockEntityRenderer<FilingCabinetBlockEntity> {
    public FilingCabinetBlockEntityRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public void render(FilingCabinetBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ItemStack stack = blockEntity.inventory.getStackInSlot(12);

        if (!stack.isEmpty()) {
            poseStack.pushPose();

            Direction facing = blockEntity.getBlockState().getValue(com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock.FACING);

            switch (facing) {
                case NORTH:
                    poseStack.translate(0.96F, 0.03F, 0F);
                    break;
                case SOUTH:
                    poseStack.translate(0.04F, 0.03F, 1.0F);
                    break;
                case EAST:
                    poseStack.translate(1.0F, 0.03F, 0.96F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(270));
                    break;
                case WEST:
                    poseStack.translate(0F, 0.03F, 0.04F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(90));
                    break;
                default:
                    break;
            }

            poseStack.scale(0.08F, 0.08F, 0.08F);

            Direction facingForLight = blockEntity.getBlockState().getValue(com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock.FACING);
            BlockPos lightSamplePos = blockEntity.getBlockPos().relative(facingForLight);
            int light = getLightLevel(blockEntity.getLevel(), lightSamplePos);

            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    blockEntity.getLevel(),
                    1
            );

            poseStack.popPose();
        }
    }

    private int getLightLevel(Level level, BlockPos pos) {
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(blockLight, skyLight);
    }
}
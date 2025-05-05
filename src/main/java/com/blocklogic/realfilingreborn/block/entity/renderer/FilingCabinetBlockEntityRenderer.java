package com.blocklogic.realfilingreborn.block.entity.renderer;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

public class FilingCabinetBlockEntityRenderer implements BlockEntityRenderer<FilingCabinetBlockEntity> {

    public FilingCabinetBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // Constructor - doesn't need any special setup
    }

    @Override
    public void render(FilingCabinetBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        // Get proper light level from the block position
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(FilingCabinetBlock.FACING);

        // Calculate the lighting at the block position
        int blockLight = level.getBrightness(LightLayer.BLOCK, blockEntity.getBlockPos());
        int skyLight = level.getBrightness(LightLayer.SKY, blockEntity.getBlockPos());
        //int itemLight = LightTexture.pack(blockLight, skyLight);

        // If you want items to always be fully visible regardless of lighting:
        int itemLight = 0xF000F0; // Full brightness - uncomment if preferred

        // Save original transformation state
        poseStack.pushPose();

        // Move to the correct base position for rendering
        poseStack.translate(0.5, 0.0, 0.5); // Center of the block at the bottom

        // Loop through folders in the cabinet
        for (int slot = 0; slot < blockEntity.inventory.getSlots(); slot++) {
            ItemStack folderStack = blockEntity.inventory.getStackInSlot(slot);

            if (!folderStack.isEmpty()) {
                // Get stored item
                ItemStack storedItem = null;

                if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                    FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                    if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                        storedItem = new ItemStack(BuiltInRegistries.ITEM.get(contents.storedItemId().get()));
                    }
                } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                    NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                    if (contents != null && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                        storedItem = contents.storedItems().get(0).stack().copy();
                    }
                }

                if (storedItem != null) {
                    // Calculate position for this slot
                    float offsetX = (slot - 2) * 0.15f; // Center items with slot 2 at center

                    // Render item
                    renderItem(storedItem, offsetX, poseStack, bufferSource, itemLight, facing);
                }
            }
        }

        poseStack.popPose();
    }

    private void renderItem(ItemStack stack, float offsetX, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Direction facing) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();

        // Scale down the item
        float scale = 0.12f; // 20% of normal size
        poseStack.scale(scale, scale, scale);

        // Apply rotation based on cabinet facing direction
        switch (facing) {
            case NORTH -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(0));
                poseStack.translate(offsetX / scale, 0.1f / scale, -0.503f / scale); // Front face, slightly offset
            }
            case SOUTH -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                poseStack.translate(offsetX / scale, 0.1f / scale, -0.503f / scale); // Front face, slightly offset
            }
            case EAST -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
                poseStack.translate(offsetX / scale, 0.1f / scale, 0.503f / scale); // Front face, slightly offset
            }
            case WEST -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(270));
                poseStack.translate(offsetX / scale, 0.1f / scale, 0.503f / scale); // Front face, slightly offset
            }
        }

        // Render the item
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                null,
                0
        );

        poseStack.popPose();
    }
}
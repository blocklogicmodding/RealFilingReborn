package com.blocklogic.realfilingreborn.block.entity.renderer;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import com.blocklogic.realfilingreborn.util.FormattingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class FilingCabinetBlockEntityRenderer implements BlockEntityRenderer<FilingCabinetBlockEntity> {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Font FONT = MC.font;

    public FilingCabinetBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FilingCabinetBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(FilingCabinetBlock.FACING);

        int itemLight = 0xF000F0;

        poseStack.pushPose();

        setupFaceTransform(poseStack, facing);

        for (int slot = 0; slot < blockEntity.inventory.getSlots(); slot++) {
            ItemStack folderStack = blockEntity.inventory.getStackInSlot(slot);

            if (!folderStack.isEmpty()) {
                ItemStack storedItem = null;
                String countText = "";

                if (folderStack.getItem() instanceof FilingFolderItem && !(folderStack.getItem() instanceof NBTFilingFolderItem)) {
                    FilingFolderItem.FolderContents contents = folderStack.get(FilingFolderItem.FOLDER_CONTENTS.value());
                    if (contents != null && contents.storedItemId().isPresent() && contents.count() > 0) {
                        storedItem = new ItemStack(BuiltInRegistries.ITEM.get(contents.storedItemId().get()));
                        countText = FormattingCache.getFormattedItemCount(contents.count());
                    }
                } else if (folderStack.getItem() instanceof NBTFilingFolderItem) {
                    NBTFilingFolderItem.NBTFolderContents contents = folderStack.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());
                    if (contents != null && contents.storedItemId().isPresent() && !contents.storedItems().isEmpty()) {
                        storedItem = contents.storedItems().get(0).stack().copy();
                        countText = String.valueOf(contents.storedItems().size());
                    }
                }

                if (storedItem != null) {
                    float offsetX = (slot - 2) * 0.15f;

                    renderSlotContent(storedItem, countText, offsetX, poseStack, bufferSource, itemLight);
                }
            }
        }

        poseStack.popPose();
    }

    private void setupFaceTransform(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.5, 0.5);

        poseStack.mulPose(Axis.YP.rotationDegrees(180));

        switch (facing) {
            case NORTH -> poseStack.translate(0, 0.025, 0.475);
            case EAST -> {
                poseStack.translate(-0.475, 0.025, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case SOUTH -> {
                poseStack.translate(0, 0.025, -0.475);
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            case WEST -> {
                poseStack.translate(0.475, 0.025, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
        }

        poseStack.translate(0, 0, -0.5/16D);
    }

    private void renderSlotContent(ItemStack stack, String countText, float offsetX, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(offsetX, -0.2f, 0);

        poseStack.pushPose();
        poseStack.scale(0.15f, 0.15f, 0.15f);
        MC.getItemRenderer().renderStatic(
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

        if (!countText.isEmpty()) {
            renderText(countText, poseStack, bufferSource, packedLight);
        }

        poseStack.popPose();
    }

    private void renderText(String text, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0, -0.08f, 0.001f);

        poseStack.scale(0.004f, 0.0045f, 0.004f);
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
}
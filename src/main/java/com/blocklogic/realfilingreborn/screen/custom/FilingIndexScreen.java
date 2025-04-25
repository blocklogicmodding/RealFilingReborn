package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.client.RangeVisualizationManager;
import com.blocklogic.realfilingreborn.client.RangeVisualizationRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

public class FilingIndexScreen extends AbstractContainerScreen<FilingIndexMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "textures/gui/filing_index_gui.png");

    private boolean rangeVisualizationActive = false;
    private RangeButton rangeButton;

    public FilingIndexScreen(FilingIndexMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private class RangeButton extends AbstractButton {
        public RangeButton(int x, int y) {
            super(x, y, 8, 8, Component.translatable("tooltip.realfilingreborn.range_visualization_button"));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(0.7529F, 0.8196F, 0.8863F, 1.0F);

            int dotSize = 2;
            int dotX = getX() + (width - dotSize) / 2;
            int dotY = getY() + (height - dotSize) / 2;
            int dotColor = rangeVisualizationActive ? 0xFF657785 : 0xFFdbe0e4;
            graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, dotColor);
        }

        @Override
        public void onPress() {
            rangeVisualizationActive = !rangeVisualizationActive;
            if (minecraft.level != null && menu.blockEntity != null) {
                int range = menu.blockEntity.getRangeFromUpgrade();
                RangeVisualizationManager.toggleRangeVisualization(menu.blockEntity.getBlockPos(), range);
            }
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Check if there's an active visualization for this block when screen opens
        BlockPos pos = menu.blockEntity.getBlockPos();
        rangeVisualizationActive = RangeVisualizationManager.isVisualizationActive(pos);

        rangeButton = new RangeButton(x + 159, y + 9);
        addRenderableWidget(rangeButton);
    }


    @Override
    protected void renderBg(GuiGraphics guiGraphics, float v, int i, int i1) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        if (rangeButton.isHoveredOrFocused()) {
            Component tooltipText = Component.translatable(rangeVisualizationActive ?
                    "tooltip.realfilingreborn.range_visualization_on" :
                    "tooltip.realfilingreborn.range_visualization_off");
            guiGraphics.renderTooltip(font, tooltipText, mouseX, mouseY);
        }
    }
}
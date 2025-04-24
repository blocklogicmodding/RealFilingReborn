package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import com.blocklogic.realfilingreborn.client.RangeVisualizationManager;
import com.blocklogic.realfilingreborn.client.RangeVisualizationRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
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
    private ImageButton rangeButton;

    public FilingIndexScreen(FilingIndexMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private static final WidgetSprites RANGE_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "gui/range_button"),
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "gui/range_button_hover")
    );

    @Override
    protected void init() {
        super.init();

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        rangeButton = new ImageButton(
                x + 159, y + 9, 8, 8,
                RANGE_BUTTON_SPRITES,
                button -> {
                    rangeVisualizationActive = !rangeVisualizationActive;
                    BlockPos pos = menu.blockEntity.getBlockPos();
                    Level level = this.minecraft.level;

                    if (level != null && level.getBlockEntity(pos) instanceof FilingIndexBlockEntity blockEntity) {
                        int range = blockEntity.getRangeFromUpgrade();
                        RangeVisualizationManager.toggleRangeVisualization(pos, range);
                    }
                },
                Component.translatable("tooltip.realfilingreborn.range_visualization_button")
        );

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

        // Add tooltip for the range button
        if (rangeButton.isHoveredOrFocused()) {
            Component tooltipText = Component.translatable(rangeVisualizationActive ?
                    "tooltip.realfilingreborn.range_visualization_on" :
                    "tooltip.realfilingreborn.range_visualization_off");
            guiGraphics.renderTooltip(font, tooltipText, mouseX, mouseY);
        }
    }
}
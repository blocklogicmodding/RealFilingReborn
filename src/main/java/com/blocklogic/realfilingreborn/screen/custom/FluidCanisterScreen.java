package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.network.ExtractionPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class FluidCanisterScreen extends AbstractContainerScreen<FluidCanisterMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "textures/gui/assignment_gui.png");

    private static final int EXTRACT_BUTTON_X = 154;
    private static final int EXTRACT_BUTTON_Y = 45;
    private static final int EXTRACT_BUTTON_SIZE = 12;

    public FluidCanisterScreen(FluidCanisterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 154;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        boolean extractHover = isMouseOverButton(mouseX, mouseY,
                x + EXTRACT_BUTTON_X, y + EXTRACT_BUTTON_Y,
                EXTRACT_BUTTON_SIZE, EXTRACT_BUTTON_SIZE);
        renderExtractButton(guiGraphics, x + EXTRACT_BUTTON_X, y + EXTRACT_BUTTON_Y, extractHover);
    }

    private void renderExtractButton(GuiGraphics guiGraphics, int x, int y, boolean hover) {
        int u = hover ? 188 : 176;
        int v = 0;
        guiGraphics.blit(GUI_TEXTURE, x, y, u, v, EXTRACT_BUTTON_SIZE, EXTRACT_BUTTON_SIZE);
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, int buttonX, int buttonY, int width, int height) {
        return mouseX >= buttonX && mouseX < buttonX + width &&
                mouseY >= buttonY && mouseY < buttonY + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (isMouseOverButton(mouseX, mouseY,
                x + EXTRACT_BUTTON_X, y + EXTRACT_BUTTON_Y,
                EXTRACT_BUTTON_SIZE, EXTRACT_BUTTON_SIZE)) {
            PacketDistributor.sendToServer(new ExtractionPacket(ExtractionPacket.ExtractionType.CANISTER));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (isMouseOverButton(mouseX, mouseY,
                x + EXTRACT_BUTTON_X, y + EXTRACT_BUTTON_Y,
                EXTRACT_BUTTON_SIZE, EXTRACT_BUTTON_SIZE)) {
            Component tooltip = Component.translatable("gui.realfilingreborn.extract_fluid");
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        Component instruction = Component.translatable("gui.realfilingreborn.canister.instruction");

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
        int textWidth = this.font.width(instruction);
        int scaledX = (int)((this.imageWidth - textWidth * 0.8f) / 2 / 0.8f);
        int scaledY = (int)(20 / 0.8f);
        guiGraphics.drawString(this.font, instruction, scaledX, scaledY, 0x2a2add, false);
        guiGraphics.pose().popPose();

        Component countText = menu.getCurrentCountText();
        if (countText != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
            int countWidth = this.font.width(countText);
            int scaledCountX = (int)((this.imageWidth - countWidth * 0.8f) / 2 / 0.8f);
            int scaledCountY = (int)(30 / 0.8f);
            guiGraphics.drawString(this.font, countText, scaledCountX, scaledCountY, 0x404040, false);
            guiGraphics.pose().popPose();
        }
    }
}
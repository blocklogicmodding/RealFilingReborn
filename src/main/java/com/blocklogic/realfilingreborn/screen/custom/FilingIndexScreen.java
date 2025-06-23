package com.blocklogic.realfilingreborn.screen.custom;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.util.FormattingCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FilingIndexScreen extends AbstractContainerScreen<FilingIndexMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "textures/gui/filing_index_gui.png");
    private static final ResourceLocation GUI_ELEMENTS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "textures/gui/gui_elements.png");

    // Button positions and sizes
    private static final int SORT_NAME_X = 256;
    private static final int SORT_NAME_Y = 0;
    private static final int SORT_QUANTITY_X = 256;
    private static final int SORT_QUANTITY_Y = 18;
    private static final int SORT_MOD_X = 256;
    private static final int SORT_MOD_Y = 36;
    private static final int BUTTON_SIZE = 18;

    // GUI components
    private EditBox searchBox;

    // Search and sorting state
    private String searchText = "";
    private SortMode sortMode = SortMode.NAME;

    public FilingIndexScreen(FilingIndexMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
        this.inventoryLabelY = -1000; // Hide inventory label as specified
    }

    @Override
    protected void init() {
        super.init();

        // Search bar: x122, y8, Size: 102w, 6h
        searchBox = new EditBox(this.font, leftPos + 122, topPos + 8, 102, 16, Component.literal("Search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setValue(searchText);
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Main GUI background
        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // Render buttons
        renderButton(guiGraphics, x, y, SORT_NAME_X, SORT_NAME_Y,
                0, 0, 18, 0, mouseX, mouseY); // Sort by Name
        renderButton(guiGraphics, x, y, SORT_QUANTITY_X, SORT_QUANTITY_Y,
                0, 18, 18, 18, mouseX, mouseY); // Sort by Quantity
        renderButton(guiGraphics, x, y, SORT_MOD_X, SORT_MOD_Y,
                0, 36, 18, 36, mouseX, mouseY); // Sort by Mod

        // Render scrollbar handle if needed
        renderScrollbar(guiGraphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render title
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);

        // Don't render inventory label (inventoryLabelY is set to -1000)
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        int storageStartSlot = 36; // After player inventory
        int storageEndSlot = storageStartSlot + 96; // 96 storage slots
        int slotIndex = this.menu.slots.indexOf(slot);

        if (slotIndex >= storageStartSlot && slotIndex < Math.min(storageEndSlot, this.menu.slots.size())) {
            // Custom rendering for storage slots
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                // Render the item icon
                guiGraphics.renderItem(stack, slot.x, slot.y);

                // Only render our custom count if > 1
                if (stack.getCount() > 1) {
                    String countText = FormattingCache.getFormattedItemCount(stack.getCount());

                    // Calculate proper text positioning
                    float scale = 0.6f;
                    int textWidth = this.font.width(countText);

                    // Position text at bottom-right corner of the slot
                    // slot.x and slot.y are already relative to the GUI
                    int textX = slot.x + 16 - (int)(textWidth * scale) - 1;
                    int textY = slot.y + 16 - (int)(this.font.lineHeight * scale) - 1;

                    // Render with smaller scale and proper z-level
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(textX, textY, 200);
                    guiGraphics.pose().scale(scale, scale, 1.0f);

                    // Black outline for visibility (draw in all 4 directions)
                    guiGraphics.drawString(this.font, countText, 1, 0, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, -1, 0, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, 0, 1, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, 0, -1, 0x000000, false);

                    // Corner outlines for better visibility
                    guiGraphics.drawString(this.font, countText, 1, 1, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, -1, -1, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, 1, -1, 0x000000, false);
                    guiGraphics.drawString(this.font, countText, -1, 1, 0x000000, false);

                    // White text on top
                    guiGraphics.drawString(this.font, countText, 0, 0, 0xFFFFFF, false);

                    guiGraphics.pose().popPose();

                    // Debug: Print to console to verify counts are being rendered
                    if (stack.getCount() > 64) { // Only log large counts to avoid spam
                        System.out.println("Rendering count " + countText + " for " + stack.getItem().toString());
                    }
                }
            }
        } else {
            // Normal rendering for player inventory slots
            super.renderSlot(guiGraphics, slot);
        }
    }

    private void renderButton(GuiGraphics guiGraphics, int guiX, int guiY, int buttonX, int buttonY,
                              int normalU, int normalV, int hoverU, int hoverV, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= guiX + buttonX && mouseX < guiX + buttonX + BUTTON_SIZE &&
                mouseY >= guiY + buttonY && mouseY < guiY + buttonY + BUTTON_SIZE;

        if (isHovered) {
            guiGraphics.blit(GUI_ELEMENTS_TEXTURE, guiX + buttonX, guiY + buttonY, hoverU, hoverV, BUTTON_SIZE, BUTTON_SIZE);
        } else {
            guiGraphics.blit(GUI_ELEMENTS_TEXTURE, guiX + buttonX, guiY + buttonY, normalU, normalV, BUTTON_SIZE, BUTTON_SIZE);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int x, int y) {
        // Scrollbar handle position: x236, y22 to y156 (134 pixel range)
        // For now, just render a static scrollbar - implement scrolling logic later
        RenderSystem.setShaderTexture(0, GUI_ELEMENTS_TEXTURE);

        // Scrollbar handle: x0, y72, Size: 10w x 6h from gui_elements.png
        int handleX = x + 236;
        int handleY = y + 22; // Static position for now
        guiGraphics.blit(GUI_ELEMENTS_TEXTURE, handleX, handleY, 0, 72, 10, 6);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Check button clicks
        if (isMouseOverButton(mouseX, mouseY, x + SORT_NAME_X, y + SORT_NAME_Y)) {
            setSortMode(SortMode.NAME);
            return true;
        }

        if (isMouseOverButton(mouseX, mouseY, x + SORT_QUANTITY_X, y + SORT_QUANTITY_Y)) {
            setSortMode(SortMode.QUANTITY);
            return true;
        }

        if (isMouseOverButton(mouseX, mouseY, x + SORT_MOD_X, y + SORT_MOD_Y)) {
            setSortMode(SortMode.MOD);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, int buttonX, int buttonY) {
        return mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE &&
                mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void onSearchChanged(String newText) {
        this.searchText = newText;
        // TODO: Implement search filtering
        refreshDisplayedItems();
    }

    private void setSortMode(SortMode mode) {
        this.sortMode = mode;
        // TODO: Implement sorting
        refreshDisplayedItems();
    }

    private void refreshDisplayedItems() {
        // TODO: Implement item filtering and sorting based on searchText and sortMode
        // This will need to communicate with the menu to update displayed slots
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    public enum SortMode {
        NAME,
        QUANTITY,
        MOD
    }
}
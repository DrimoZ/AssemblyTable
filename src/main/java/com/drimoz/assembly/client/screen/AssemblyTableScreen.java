package com.drimoz.assembly.client.screen;

import com.drimoz.assembly.client.menu.AssemblyTableMenu;
import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import com.drimoz.assembly.core.utils.Constants;
import com.drimoz.assembly.core.utils.StringHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import static com.drimoz.assembly.core.utils.Constants.Screen.*;

public class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {

    private final AssemblyTableBlockEntity TE;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);

        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;

        TE = menu.getBlockEntity();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        // Draw the background image

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        if (TE.getCurrentEnergy() > 0) {
            int filledEnergyBarHeight = (int) ((double) TE.getCurrentEnergy() / TE.getTotalEnergy() * ENERGY_BAR_HEIGHT);
            guiGraphics.blit(
                    GUI_TEXTURE,
                    this.leftPos + ENERGY_BAR_X, this.topPos + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - filledEnergyBarHeight,
                    FILLED_ENERGY_BAR_X, FILLED_ENERGY_BAR_Y,
                    ENERGY_BAR_WIDTH, filledEnergyBarHeight,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
        }

        if (TE.getRecipeCurrentProgress() > 0) {
            int filledProgressBarHeight = (int) ((double) TE.getRecipeCurrentProgress() / TE.getRecipeTotalProgress() * PROGRESS_BAR_HEIGHT);
            guiGraphics.blit(
                    GUI_TEXTURE,
                    this.leftPos + PROGRESS_BAR_X, this.topPos + PROGRESS_BAR_Y + PROGRESS_BAR_HEIGHT - filledProgressBarHeight,
                    FILLED_PROGRESS_BAR_X, FILLED_PROGRESS_BAR_Y,
                    PROGRESS_BAR_WIDTH, filledProgressBarHeight,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        renderSelectedSlots(guiGraphics, mouseX, mouseY);

        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, (this.imageHeight - 96 + 2), 4210752, false);
    }

    // Inner Work

    private void renderSelectedSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int slotIndex : TE.getSelectedRecipeSlots()) {
            guiGraphics.blit(
                    GUI_TEXTURE,
                     this.leftPos + Constants.Menu.SHADOW_FIRST_SLOT_X + (slotIndex % Constants.Menu.SHADOW_COLUMN_COUNT) * SLOT_WIDTH - 1,
                     this.topPos + Constants.Menu.SHADOW_FIRST_SLOT_Y + (slotIndex / Constants.Menu.SHADOW_COLUMN_COUNT) * SLOT_HEIGHT - 1,
                    SLOT_X, SLOT_Y,
                    SLOT_WIDTH, SLOT_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT
            );

        }
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NumberFormat format = DecimalFormat.getNumberInstance();
        if (isMouseOverEnergyBar(mouseX, mouseY)) {
            String currentEnergy = format.format(TE.getCurrentEnergy());
            String energyCapacity = format.format(TE.getTotalEnergy());
            currentEnergy = currentEnergy.replaceAll("\u00A0", ",").replaceAll(" ", ",");
            energyCapacity = energyCapacity.replaceAll("\u00A0", ",").replaceAll(" ", ",");

            Component tooltip = Component.literal(
                    "§6" + currentEnergy + " §f/ §c" + energyCapacity + " §4" +
                            StringHelper.tooltipComponent("energy_name").getString()
            );
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }

        if (isMouseOverProgressBar(mouseX, mouseY)) {
            int currentProgress = TE.getRecipeCurrentProgress();
            int totalProgress = TE.getRecipeTotalProgress();

            Component tooltip = Component.literal(
                    (totalProgress > 0 ? (int) ((double) currentProgress / totalProgress * 100) + "%" : "0%")
            );
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private boolean isMouseOverEnergyBar(int mouseX, int mouseY) {
        int x1 = this.leftPos + ENERGY_BAR_X;
        int y1 = this.topPos + ENERGY_BAR_Y;
        int x2 = x1 + ENERGY_BAR_WIDTH;
        int y2 = y1 + ENERGY_BAR_HEIGHT;

        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    private boolean isMouseOverProgressBar(int mouseX, int mouseY) {
        int x1 = this.leftPos + PROGRESS_BAR_X;
        int y1 = this.topPos + PROGRESS_BAR_Y;
        int x2 = x1 + PROGRESS_BAR_WIDTH;
        int y2 = y1 + PROGRESS_BAR_HEIGHT;

        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }
}

package dev.drimoz.laserassembly.client.laser;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.laser.LaserMenu;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.SlotItemHandler;

import static dev.drimoz.laserassembly.content.laser.LaserBlockEntity.*;
import static dev.drimoz.laserassembly.content.laser.LaserMenu.CONTENT_BOTTOM;

/** Energy in column 0, the lens in column 1, then a line each for intensity, beam and what the laser is doing. */
public class LaserScreen extends AbstractContainerScreen<LaserMenu> {

    private static final int TEXT_X = GuiMetrics.column(2) + 2;
    private static final int BUTTON_SIZE = 12;
    /** Minus then plus, right-aligned on the intensity row. */
    private static final int MINUS_X = GuiMetrics.column(7) + 3;
    private static final int PLUS_X = GuiMetrics.column(8) + 3;
    private static final int BUTTON_Y = GuiMetrics.row(0) + 3;

    public LaserScreen(LaserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GuiMetrics.WIDTH;
        imageHeight = GuiMetrics.height(CONTENT_BOTTOM);
        titleLabelX = GuiMetrics.MARGIN;
        titleLabelY = GuiMetrics.TITLE_Y;
        inventoryLabelX = GuiMetrics.MARGIN;
        inventoryLabelY = GuiMetrics.inventoryLabelY(CONTENT_BOTTOM);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GuiSprites.panel(graphics, x, y, imageWidth, imageHeight);
        int barrelOpen = menu.laser().effects().barrel();
        for (Slot slot : menu.slots) {
            // Barrel slots stay hatched until a Barrel augment opens them.
            boolean closed = slot instanceof SlotItemHandler handler && handler.getItemHandler() == menu.laser().barrel()
                    && slot.getSlotIndex() >= barrelOpen && !slot.hasItem();
            if (closed) GuiSprites.disabledSlot(graphics, x + slot.x - 1, y + slot.y - 1);
            else GuiSprites.slot(graphics, x + slot.x - 1, y + slot.y - 1, false);
        }
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_ENERGY, x + GuiMetrics.GAUGE_X, y + GuiMetrics.CONTENT_TOP,
                GuiMetrics.GAUGE_WIDTH, CONTENT_BOTTOM - GuiMetrics.CONTENT_TOP, (float) menu.data(DATA_ENERGY) / ENERGY_CAPACITY);
        button(graphics, MINUS_X, "-", mouseX, mouseY);
        button(graphics, PLUS_X, "+", mouseX, mouseY);
        GuiSprites.statusLight(graphics, switch (menu.data(DATA_STATE)) {
            case STATE_FIRING -> GuiTheme.Status.WORKING;
            case STATE_HOLDING -> GuiTheme.Status.WAITING;
            // Aimed, but its beam does not fit the step: a setting to change, not a fault.
            case STATE_TOO_WEAK, STATE_TOO_STRONG, STATE_WRONG_BEAM -> GuiTheme.Status.BLOCKED;
            default -> GuiTheme.Status.PROBLEM;
        }, x + GuiMetrics.LED_X, y + GuiMetrics.LED_Y);
    }

    private void button(GuiGraphics graphics, int left, String label, int mouseX, int mouseY) {
        GuiSprites.button(graphics, leftPos + left, topPos + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, over(left, mouseX, mouseY));
        graphics.drawString(font, label, leftPos + left + (BUTTON_SIZE - font.width(label) + 1) / 2, topPos + BUTTON_Y + 2, GuiTheme.TEXT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.TEXT, false);

        int intensity = menu.data(DATA_INTENSITY);
        graphics.drawString(font, text("laser.intensity", intensity, MAX_INTENSITY), TEXT_X, GuiMetrics.row(0) + 5, GuiTheme.TEXT, false);
        ResourceLocation beam = menu.laser().beam();
        graphics.drawString(font, beam == null ? text("beam.raw") : BeamType.name(beam), TEXT_X, GuiMetrics.row(1) + 5, GuiTheme.TEXT_MUTED, false);

        BlockPos target = menu.laser().target();
        Component state = target == null ? text("laser.no_target") : switch (menu.data(DATA_STATE)) {
            case STATE_FIRING -> text("laser.firing");
            case STATE_NO_POWER -> text("laser.no_power");
            case STATE_TOO_WEAK -> text("laser.too_weak");
            case STATE_TOO_STRONG -> text("laser.too_strong");
            case STATE_WRONG_BEAM -> text("laser.wrong_beam");
            default -> text("laser.holding");
        };
        graphics.drawString(font, state, TEXT_X, GuiMetrics.row(2) + 5, GuiTheme.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        // An empty laser slot says what it is for: three kinds sit side by side.
        if (hoveredSlot instanceof SlotItemHandler handler && !hoveredSlot.hasItem()) {
            String hint = handler.getItemHandler() == menu.laser().lens() ? "laser.slot.lens"
                    : handler.getItemHandler() == menu.laser().augments() ? "laser.slot.augment"
                    : hoveredSlot.getSlotIndex() < menu.laser().effects().barrel() ? "laser.slot.barrel" : "laser.slot.barrel_closed";
            graphics.renderTooltip(font, text(hint), mouseX, mouseY);
        }
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= GuiMetrics.GAUGE_X && x < GuiMetrics.GAUGE_X + GuiMetrics.GAUGE_WIDTH && y >= GuiMetrics.CONTENT_TOP && y < CONTENT_BOTTOM) {
            int cost = Mth.ceil(menu.data(DATA_INTENSITY) * ServerConfig.tuning().fePerIntensity());
            graphics.renderComponentTooltip(font, java.util.List.of(
                    text("gui.energy", menu.data(DATA_ENERGY), ENERGY_CAPACITY), text("laser.cost", cost)), mouseX, mouseY);
        } else if (y >= GuiMetrics.row(2) && y < GuiMetrics.row(3) && x >= TEXT_X && menu.laser().target() != null) {
            BlockPos target = menu.laser().target();
            graphics.renderTooltip(font, text("laser.target", target.getX(), target.getY(), target.getZ()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int id = over(MINUS_X, mouseX, mouseY) ? 0 : over(PLUS_X, mouseX, mouseY) ? 1 : -1;
        if (id < 0) return super.mouseClicked(mouseX, mouseY, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    private boolean over(int left, double mouseX, double mouseY) {
        double x = mouseX - leftPos - left, y = mouseY - topPos - BUTTON_Y;
        return x >= 0 && x < BUTTON_SIZE && y >= 0 && y < BUTTON_SIZE;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}

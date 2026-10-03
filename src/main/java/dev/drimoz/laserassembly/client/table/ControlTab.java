package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.RedstoneMode;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.IntConsumer;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.DATA_CONTROL;

/**
 * FactoryIO's control tab: a switch over everything, then what redstone does - ignored, work below a
 * threshold (at 1, the native pause on any signal), or work at it and above.
 */
final class ControlTab extends SideTab {

    private static final ItemStack ICON = new ItemStack(Items.REDSTONE);

    private static final int WIDTH = 112;
    private static final int ROW = 14;
    private static final int BUTTON_H = 12;
    private static final int SMALL = 11;

    private final AssemblyTableMenu menu;
    private final IntConsumer click;

    ControlTab(AssemblyTableMenu menu, IntConsumer click) {
        super("control", Side.RIGHT, GuiTheme.TAB_CONTROL);
        this.menu = menu;
        this.click = click;
    }

    @Override
    protected Component title() {
        return text("tab.control");
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        GuiSprites.icon(graphics, ICON, x, y);
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return (2 + RedstoneMode.values().length) * ROW;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int control = menu.data(DATA_CONTROL);
        graphics.drawString(font, text("control.switch"), x, y + 2, GuiTheme.TAB_LABEL, true);
        GuiSprites.toggle(graphics, x + WIDTH - GuiSprites.SWITCH_W, y + 2, (control & 1) != 0);

        RedstoneMode current = mode(control);
        for (RedstoneMode mode : RedstoneMode.values()) {
            int rowY = y + (1 + mode.ordinal()) * ROW;
            GuiSprites.button(graphics, x, rowY, WIDTH, BUTTON_H, mode == current || inside(mouseX, mouseY, x, rowY, WIDTH, BUTTON_H));
            Component label = text("control.mode." + mode.name().toLowerCase());
            graphics.drawString(font, label, x + (WIDTH - font.width(label)) / 2, rowY + 2,
                    mode == current ? 0xFF2E8B2E : GuiTheme.TEXT, false);
        }

        int thresholdY = y + (1 + RedstoneMode.values().length) * ROW;
        boolean usesThreshold = current != RedstoneMode.ALWAYS;
        graphics.drawString(font, text("control.threshold", threshold(control)), x, thresholdY + 2,
                usesThreshold ? GuiTheme.TAB_VALUE : GuiTheme.TAB_MUTED, true);
        if (usesThreshold) {
            small(graphics, font, x + WIDTH - 2 * SMALL - 2, thresholdY, "-", mouseX, mouseY);
            small(graphics, font, x + WIDTH - SMALL, thresholdY, "+", mouseX, mouseY);
        }
    }

    private static void small(GuiGraphics graphics, Font font, int x, int y, String label, int mouseX, int mouseY) {
        GuiSprites.button(graphics, x, y, SMALL, SMALL, inside(mouseX, mouseY, x, y, SMALL, SMALL));
        graphics.drawString(font, label, x + (SMALL - font.width(label) + 1) / 2, y + 2, GuiTheme.TEXT, false);
    }

    @Override
    protected boolean contentClicked(int x, int y, double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, x, y, WIDTH, ROW)) {
            click.accept(AssemblyTableMenu.BUTTON_SWITCH);
            return true;
        }
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (inside(mouseX, mouseY, x, y + (1 + mode.ordinal()) * ROW, WIDTH, BUTTON_H)) {
                click.accept(AssemblyTableMenu.BUTTON_MODE + mode.ordinal());
                return true;
            }
        }
        int thresholdY = y + (1 + RedstoneMode.values().length) * ROW;
        if (mode(menu.data(DATA_CONTROL)) == RedstoneMode.ALWAYS) return false;
        for (int up = 0; up < 2; up++) {
            if (inside(mouseX, mouseY, x + WIDTH - (up == 0 ? 2 * SMALL + 2 : SMALL), thresholdY, SMALL, SMALL)) {
                click.accept(AssemblyTableMenu.BUTTON_THRESHOLD + up);
                return true;
            }
        }
        return false;
    }

    private static RedstoneMode mode(int control) {
        return RedstoneMode.values()[(control >> 1) & 3];
    }

    private static int threshold(int control) {
        return (control >> 3) & 15;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
